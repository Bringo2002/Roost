package com.roost.dto;

import com.roost.model.VerificationDocument;

import java.time.LocalDateTime;

/**
 * What a lister sees about a document they submitted. It deliberately has
 * no storage key, checksum, reviewer or AI opinion: the file is only ever
 * reachable through the server.
 *
 * @param id the document's id
 * @param docType which document it is, e.g. {@code NATIONAL_ID_FRONT}
 * @param status {@code SUBMITTED}, {@code ACCEPTED} or {@code REJECTED}
 * @param uploadedAt when it was submitted
 * @param rejectReason why a reviewer rejected it, or null
 */
public record VerificationDocumentDto(Long id, String docType, String status,
                                      LocalDateTime uploadedAt, String rejectReason) {

    public static VerificationDocumentDto from(VerificationDocument document) {
        return new VerificationDocumentDto(
                document.getId(),
                document.getDocType().name(),
                document.getStatus().name(),
                document.getUploadedAt(),
                document.getRejectReason());
    }
}
