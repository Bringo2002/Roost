package com.roost.controller;

import com.roost.model.Application;
import com.roost.model.User;
import com.roost.service.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@CrossOrigin(origins = "*")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<Application> submitApplication(@AuthenticationPrincipal User user,
                                                            @RequestBody Map<String, Object> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(applicationService.submitApplication(user, payload));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<List<Application>> getPropertyApplications(@PathVariable Long propertyId,
                                                                        @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(applicationService.getPropertyApplications(user, propertyId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Application>> getMyApplications(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(applicationService.getMyApplications(user));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Application> updateStatus(@PathVariable Long id,
                                                       @RequestBody Map<String, String> payload,
                                                       @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(applicationService.updateStatus(user, id, payload.get("status")));
    }
}
