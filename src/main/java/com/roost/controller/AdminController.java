package com.roost.controller;

import com.roost.dto.PropertyReportResponseDto;
import com.roost.dto.PropertyResponseDto;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.PropertyService;
import com.roost.repository.UserRepository;
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
public class AdminController {

    private final PropertyService propertyService;
    private final UserRepository userRepository;

    public AdminController(PropertyService propertyService, UserRepository userRepository) {
        this.propertyService = propertyService;
        this.userRepository = userRepository;
    }

    @GetMapping("/pending-verifications")
    public ResponseEntity<?> getPendingVerifications(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(PropertyResponseDto.from(propertyService.getPendingPhotoReview()));
    }

    @PostMapping("/properties/{id}/approve-photos")
    public ResponseEntity<?> approvePhotos(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        Property updated = propertyService.approvePhotos(id);
        return ResponseEntity.ok(PropertyResponseDto.from(updated));
    }

    @PostMapping("/properties/{id}/reject-photos")
    public ResponseEntity<?> rejectPhotos(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        String reason = body != null ? body.get("reason") : null;
        Property updated = propertyService.rejectPhotos(id, reason);
        return ResponseEntity.ok(PropertyResponseDto.from(updated));
    }

    @GetMapping("/flagged-listings")
    public ResponseEntity<?> getFlaggedListings(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(PropertyResponseDto.from(propertyService.getFlaggedForReview()));
    }

    @GetMapping("/properties/{id}/reports")
    public ResponseEntity<?> getReportsForProperty(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(PropertyReportResponseDto.from(propertyService.getReportsForProperty(id)));
    }

    /** Admin manually hides a listing over reports that haven't hit the
     *  auto-hide threshold -- see PropertyService.hideListing. */
    @PostMapping("/properties/{id}/hide")
    public ResponseEntity<?> hideListing(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(PropertyResponseDto.from(propertyService.hideListing(id)));
    }

    /** Admin reviewed the reports and decided no action is needed --
     *  clears the listing from the flagged queue without changing its
     *  status. See PropertyService.dismissReports. */
    @PostMapping("/properties/{id}/dismiss-reports")
    public ResponseEntity<?> dismissReports(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (user.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(PropertyResponseDto.from(propertyService.dismissReports(id)));
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
        return ResponseEntity.ok(PropertyResponseDto.from(updated));
    }

    /** Disable or re-enable a user account.
     *  {@code PATCH /api/admin/users/{id}/enabled} with body {@code {"enabled": false}}.
     *  A disabled account's JWT is rejected at the Spring Security filter layer
     *  on every subsequent request — no token invalidation needed. */
    @PatchMapping("/users/{id}/enabled")
    public ResponseEntity<?> setUserEnabled(@PathVariable Long id,
                                            @RequestBody Map<String, Boolean> body,
                                            @AuthenticationPrincipal User admin) {
        if (admin == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (admin.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        Boolean enabled = body.get("enabled");
        if (enabled == null) return ResponseEntity.badRequest().body(Map.of("error", "'enabled' field is required"));
        User target = userRepository.findById(id)
                .orElseThrow(() -> com.roost.exception.ApiException.notFound("User not found"));
        target.setEnabled(enabled);
        userRepository.save(target);
        return ResponseEntity.ok(Map.of("id", id, "enabled", enabled));
    }

    /** Lock or unlock a user account.
     *  {@code PATCH /api/admin/users/{id}/locked} with body {@code {"locked": true}}.
     *  A locked account fails authentication immediately — same Spring Security
     *  path as a disabled account but semantically "temporarily suspended". */
    @PatchMapping("/users/{id}/locked")
    public ResponseEntity<?> setUserLocked(@PathVariable Long id,
                                           @RequestBody Map<String, Boolean> body,
                                           @AuthenticationPrincipal User admin) {
        if (admin == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (admin.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        Boolean locked = body.get("locked");
        if (locked == null) return ResponseEntity.badRequest().body(Map.of("error", "'locked' field is required"));
        User target = userRepository.findById(id)
                .orElseThrow(() -> com.roost.exception.ApiException.notFound("User not found"));
        target.setAccountNonLocked(!locked); // locked=true means accountNonLocked=false
        userRepository.save(target);
        return ResponseEntity.ok(Map.of("id", id, "locked", locked));
    }
}
