package com.roost.controller;

import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.GeminiDocVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

/**
 * Server-side landlord document fraud-check, moved off the Flutter
 * client -- see GeminiDocVerificationService for why. Same auth gate
 * PropertyController.uploadPhoto already uses for landlord-only
 * uploads, and the same base64-in-JSON-body transport as that endpoint
 * rather than multipart, for consistency.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentVerificationController {

    private static final int MAX_DOC_BYTES = 10 * 1024 * 1024; // 10 MB, matches the client's own Tier 1 cap

    private final GeminiDocVerificationService geminiDocVerificationService;

    public DocumentVerificationController(GeminiDocVerificationService geminiDocVerificationService) {
        this.geminiDocVerificationService = geminiDocVerificationService;
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> payload, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Only landlords can submit verification documents."));
        }

        String data = payload.get("data");
        if (data == null || data.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "data is required"));
        }
        String declaredDocType = payload.getOrDefault("declaredDocType", "Unknown");

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Document data is not valid base64"));
        }
        if (bytes.length > MAX_DOC_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "Document is too large (max 10MB)"));
        }

        // landlordName comes from the authenticated user's own record,
        // never the request body -- see GeminiDocVerificationService's
        // doc comment on why that matters for the name-mismatch check.
        Map<String, Object> result = geminiDocVerificationService.verify(bytes, declaredDocType, user.getName());
        return ResponseEntity.ok(result);
    }
}
