package com.roost.controller;

import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.PropertyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for the photo review tier of listing verification.
 * Every method checks Role.ADMIN explicitly in addition to whatever
 * SecurityConfig enforces at the routing layer -- defense in depth for
 * a role that controls what gets shown as "Verified" across the app.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final PropertyService propertyService;

    public AdminController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping("/pending-verifications")
    public ResponseEntity<?> getPendingVerifications(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(propertyService.getPendingPhotoReview());
    }

    @PostMapping("/properties/{id}/approve-photos")
    public ResponseEntity<?> approvePhotos(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        Property updated = propertyService.approvePhotos(id);
        return ResponseEntity.ok(updated);
    }
}
