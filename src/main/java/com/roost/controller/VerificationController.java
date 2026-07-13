package com.roost.controller;

import com.roost.model.User;
import com.roost.model.Role;
import com.roost.model.VerificationRecord;
import com.roost.model.Property;
import com.roost.service.VerificationService;
import com.roost.service.PropertyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/verification")
@CrossOrigin(origins = "*")
public class VerificationController {

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private PropertyService propertyService;

    @PostMapping("/pay")
    public ResponseEntity<?> payHoldingFee(@RequestBody Map<String, Object> payload, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            Long propertyId = Long.parseLong(payload.get("propertyId").toString());
            String tenantPhone = payload.get("tenantPhone").toString();
            
            VerificationRecord record = verificationService.payHoldingFee(propertyId, tenantPhone, user);
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-payments")
    public ResponseEntity<?> getMyPayments(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(verificationService.getTenantPayments(user));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<?> getPropertyVerifications(
            @PathVariable Long propertyId, 
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        Property prop = propertyService.getPropertyById(propertyId);
        if (prop.getOwner() == null || !prop.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(verificationService.getPropertyVerifications(propertyId));
    }

    @GetMapping("/landlord-stats")
    public ResponseEntity<?> getLandlordStats(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Only landlords can view stats"));
        }

        List<Property> myProperties = propertyService.getPropertiesByOwner(user);
        int totalListings = myProperties.size();
        int totalSecured = 0;
        double totalRevenue = 0;

        for (Property p : myProperties) {
            if (p.isHoldingFeePaid()) {
                totalSecured++;
                List<VerificationRecord> records = verificationService.getPropertyVerifications(p.getId());
                for (VerificationRecord r : records) {
                    totalRevenue += r.getAmountPaid();
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalListings", totalListings);
        stats.put("totalSecured", totalSecured);
        stats.put("totalRevenue", totalRevenue);

        return ResponseEntity.ok(stats);
    }
}
