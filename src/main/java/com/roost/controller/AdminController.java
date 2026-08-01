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
 * Admin-only endpoints for reviewing listings that need a human look:
 * photo verification, and listings flagged by enough community reports
 * to be automatically hidden pending review. Every method checks
 * Role.ADMIN explicitly in addition to whatever SecurityConfig enforces
 * at the routing layer -- defense in depth for a role that controls
 * what gets shown as "Verified" and what stays visible at all.
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

    @GetMapping("/flagged-listings")
    public ResponseEntity<?> getFlaggedListings(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(propertyService.getFlaggedForReview());
    }

    @GetMapping("/properties/{id}/reports")
    public ResponseEntity<?> getReportsForProperty(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(propertyService.getReportsForProperty(id));
    }

    /** Admin decision on a flagged listing: restore=true republishes it
     *  (reports were unfounded), restore=false leaves it hidden pending
     *  further action -- a genuinely bad listing still gets removed
     *  through the ordinary delete endpoint, not this one. */
    @PostMapping("/properties/{id}/resolve-report")
    public ResponseEntity<?> resolveReportedListing(@PathVariable Long id,
                                                      @RequestBody Map<String, Boolean> body,
                                                      @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        boolean restore = Boolean.TRUE.equals(body.get("restore"));
        Property updated = propertyService.resolveReportedListing(id, restore);
        return ResponseEntity.ok(updated);
    }
}
