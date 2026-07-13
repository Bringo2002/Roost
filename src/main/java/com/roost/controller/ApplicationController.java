package com.roost.controller;

import com.roost.model.*;
import com.roost.repository.ApplicationRepository;
import com.roost.service.PropertyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@CrossOrigin(origins = "*")
public class ApplicationController {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private PropertyService propertyService;

    @PostMapping
    public ResponseEntity<?> submitApplication(@AuthenticationPrincipal User user, @RequestBody Map<String, Object> payload) {
        if (user == null) return ResponseEntity.status(401).build();

        try {
            Long propertyId = Long.valueOf(payload.get("propertyId").toString());
            Property property = propertyService.getPropertyById(propertyId);

            // Check if already applied
            if (applicationRepository.existsByPropertyAndApplicant(property, user)) {
                return ResponseEntity.badRequest().body(Map.of("error", "You have already applied for this property"));
            }

            Application application = new Application();
            application.setProperty(property);
            application.setApplicant(user);
            application.setFullName(payload.get("fullName").toString());
            application.setNationalId(payload.get("nationalId") != null ? payload.get("nationalId").toString() : "");
            application.setEmploymentStatus(payload.get("employmentStatus") != null ? payload.get("employmentStatus").toString() : "");
            application.setMonthlyIncome(payload.get("monthlyIncome") != null ? Double.parseDouble(payload.get("monthlyIncome").toString()) : 0.0);
            application.setStatus("PENDING");
            application.setCreatedAt(LocalDateTime.now());

            Application saved = applicationRepository.save(application);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<?> getPropertyApplications(@PathVariable Long propertyId, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Only landlords can view applications"));
        }

        Property property = propertyService.getPropertyById(propertyId);
        if (property.getOwner() == null || !property.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You don't own this property"));
        }
        List<Application> apps = applicationRepository.findByPropertyOrderByCreatedAtDesc(property);
        return ResponseEntity.ok(apps);
    }

    @GetMapping("/my")
    public ResponseEntity<List<Application>> getMyApplications(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(applicationRepository.findByApplicantOrderByCreatedAtDesc(user));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Only landlords can update application status"));
        }

        Application app = applicationRepository.findById(id).orElse(null);
        if (app == null) {
            return ResponseEntity.notFound().build();
        }

        // Verify the landlord owns this property
        if (app.getProperty().getOwner() == null || !app.getProperty().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You don't own this property"));
        }

        String newStatus = payload.get("status");
        if (!"APPROVED".equals(newStatus) && !"REJECTED".equals(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status must be APPROVED or REJECTED"));
        }

        app.setStatus(newStatus);
        applicationRepository.save(app);
        return ResponseEntity.ok(app);
    }
}
