package com.roost.controller;

import com.roost.dto.AdminIdentityReviewDto;
import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.VerificationDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lets an admin review the identity documents listers submit: see who is
 * waiting, open a file, and accept or reject the set.
 *
 * <p>{@code SecurityConfig} already limits {@code /api/admin/**} to admins;
 * the role is checked here too, the way {@link AdminController} does it.
 */
@RestController
@RequestMapping("/api/admin/verification")
public class AdminVerificationController {

    /** The body of a rejection: what the lister should fix. */
    public record RejectRequest(String reason) {}

    private final VerificationDocumentService verificationDocumentService;

    public AdminVerificationController(VerificationDocumentService verificationDocumentService) {
        this.verificationDocumentService = verificationDocumentService;
    }

    /** Listers whose identity documents are waiting, with the documents on file. */
    @GetMapping("/identity/pending")
    public List<AdminIdentityReviewDto> pendingIdentities(@AuthenticationPrincipal User admin) {
        requireAdmin(admin);
        return verificationDocumentService.listPendingIdentities().stream()
                .map(user -> AdminIdentityReviewDto.from(
                        user, verificationDocumentService.listIdentityDocuments(user.getId())))
                .toList();
    }

    /**
     * Streams one stored document. The view is recorded before the file is
     * returned, and the response must never be cached.
     */
    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> documentFile(@PathVariable Long id, @AuthenticationPrincipal User admin) {
        requireAdmin(admin);
        VerificationDocumentService.ReviewFile file = verificationDocumentService.viewDocument(admin, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document\"")
                .body(file.data());
    }

    /** Accepts the lister's documents and marks their identity verified. */
    @PostMapping("/identity/{userId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable Long userId, @AuthenticationPrincipal User admin) {
        requireAdmin(admin);
        verificationDocumentService.acceptIdentity(admin, userId);
    }

    /** Rejects the lister's documents; the reason is shown to the lister. */
    @PostMapping("/identity/{userId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable Long userId, @RequestBody RejectRequest body,
                       @AuthenticationPrincipal User admin) {
        requireAdmin(admin);
        verificationDocumentService.rejectIdentity(admin, userId, body.reason());
    }

    private static void requireAdmin(User user) {
        if (user == null) {
            throw ApiException.unauthorized("Unauthorized");
        }
        if (user.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Admin access required");
        }
    }
}
