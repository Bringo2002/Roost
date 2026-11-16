package com.roost.dto;

import com.roost.model.User;
import com.roost.model.VerificationDocument;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What an admin sees when reviewing a lister's identity documents. Like the
 * lister's own view it has no storage key or checksum: the file is only ever
 * reachable through the server, one request at a time.
 *
 * @param userId the lister under review
 * @param name the name on the lister's account, to compare with the document
 * @param documents the identity documents currently on file
 */
public record AdminIdentityReviewDto(Long userId, String name, List<Document> documents) {

    /**
     * One document on file.
     *
     * @param id the document's id, used to fetch the file
     * @param docType e.g. {@code NATIONAL_ID_FRONT}
     * @param status {@code SUBMITTED}, {@code ACCEPTED} or {@code REJECTED}
     * @param contentType {@code image/jpeg}, {@code image/png} or {@code application/pdf}
     * @param sizeBytes the file size
     * @param uploadedAt when it was submitted
     * @param rejectReason why it was rejected earlier, or null
     */
    public record Document(Long id, String docType, String status, String contentType,
                           long sizeBytes, LocalDateTime uploadedAt, String rejectReason) {
        public static Document from(VerificationDocument d) {
            return new Document(d.getId(), d.getDocType().name(), d.getStatus().name(),
                    d.getContentType(), d.getSizeBytes(), d.getUploadedAt(), d.getRejectReason());
        }
    }

    public static AdminIdentityReviewDto from(User user, List<VerificationDocument> documents) {
        return new AdminIdentityReviewDto(user.getId(), user.getName(),
                documents.stream().map(Document::from).toList());
    }
}
