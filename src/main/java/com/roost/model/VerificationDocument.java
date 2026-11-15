package com.roost.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One file a lister submitted for verification (an ID, a title deed, ...).
 * The file itself is in the private bucket under {@link #getStorageKey()};
 * there is no URL. Identity documents belong to the lister; property
 * evidence belongs to an {@link AuthorityClaim}.
 */
@Entity
@Table(name = "verification_document")
public class VerificationDocument {

    public enum DocType {
        NATIONAL_ID_FRONT, NATIONAL_ID_BACK, PASSPORT,
        TITLE_DEED, LAND_RATES_RECEIPT, UTILITY_BILL,
        LETTER_OF_AUTHORITY, TENANCY_AGREEMENT, OTHER;

        /** True for documents that identify the person rather than prove a property. */
        public boolean isIdentity() {
            return this == NATIONAL_ID_FRONT || this == NATIONAL_ID_BACK || this == PASSPORT;
        }
    }

    public enum Status { SUBMITTED, ACCEPTED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @ManyToOne
    @JoinColumn(name = "claim_id")
    private AuthorityClaim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 30)
    private DocType docType;

    // Uniqueness is enforced by the database (see V6); not repeated here so
    // Hibernate does not add a second, duplicate unique constraint.
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    @Column(name = "ai_opinion", columnDefinition = "TEXT")
    private String aiOpinion;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public VerificationDocument() {}

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public AuthorityClaim getClaim() { return claim; }
    public void setClaim(AuthorityClaim claim) { this.claim = claim; }
    public DocType getDocType() { return docType; }
    public void setDocType(DocType docType) { this.docType = docType; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getAiOpinion() { return aiOpinion; }
    public void setAiOpinion(String aiOpinion) { this.aiOpinion = aiOpinion; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
