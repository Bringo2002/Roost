package com.roost.controller;

import com.roost.dto.IdentityVerificationDto;
import com.roost.dto.VerificationDocumentDto;
import com.roost.exception.ApiException;
import com.roost.model.User;
import com.roost.model.VerificationDocument;
import com.roost.service.VerificationDocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * A lister's own identity verification: submit documents and see where it
 * stands. Reviewers have their own endpoints under {@code /api/admin}.
 */
@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationDocumentService verificationDocumentService;

    public VerificationController(VerificationDocumentService verificationDocumentService) {
        this.verificationDocumentService = verificationDocumentService;
    }

    /**
     * Submits one identity document as a multipart upload.
     *
     * <p>The two parts are validated here by hand, not with
     * {@code required = true}, so a missing part gets the same clear 400 as
     * every other mistake instead of a framework error.
     *
     * @param docType {@code NATIONAL_ID_FRONT}, {@code NATIONAL_ID_BACK} or {@code PASSPORT}
     * @param file the JPEG, PNG or PDF, up to 5 MB
     * @param user the signed-in lister
     * @return 201 with the saved document's details
     */
    @PostMapping("/identity-documents")
    public ResponseEntity<VerificationDocumentDto> uploadIdentityDocument(
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal User user) {
        VerificationDocument.DocType type = parseDocType(docType);
        if (file == null) {
            throw ApiException.badRequest("Choose a file to upload.");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read. Please try again.");
        }
        VerificationDocument saved = verificationDocumentService.submitIdentityDocument(user, type, data);
        return ResponseEntity.status(HttpStatus.CREATED).body(VerificationDocumentDto.from(saved));
    }

    /**
     * Where the signed-in lister's identity verification stands.
     *
     * @param user the signed-in lister
     * @return the status and the identity documents on file
     */
    @GetMapping("/identity")
    public IdentityVerificationDto identity(@AuthenticationPrincipal User user) {
        List<VerificationDocumentDto> documents = verificationDocumentService.listIdentityDocuments(user.getId())
                .stream().map(VerificationDocumentDto::from).toList();
        return new IdentityVerificationDto(
                verificationDocumentService.getIdentityStatus(user.getId()).name(), documents);
    }

    private static VerificationDocument.DocType parseDocType(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("docType is required.");
        }
        try {
            return VerificationDocument.DocType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown document type.");
        }
    }
}
