package com.roost.controller;

import com.roost.dto.ApplicationResponseDto;
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
    public ResponseEntity<ApplicationResponseDto> submitApplication(@AuthenticationPrincipal User user,
                                                            @RequestBody Map<String, Object> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ApplicationResponseDto.from(applicationService.submitApplication(user, payload)));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<List<ApplicationResponseDto>> getPropertyApplications(@PathVariable Long propertyId,
                                                                        @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ApplicationResponseDto.from(applicationService.getPropertyApplications(user, propertyId)));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ApplicationResponseDto>> getMyApplications(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ApplicationResponseDto.from(applicationService.getMyApplications(user)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApplicationResponseDto> updateStatus(@PathVariable Long id,
                                                       @RequestBody Map<String, String> payload,
                                                       @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ApplicationResponseDto.from(applicationService.updateStatus(user, id, payload.get("status"))));
    }
}
