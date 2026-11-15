package com.roost.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A lister's claim that they own a property, or act for its owner. A
 * reviewer decides it; see {@link VerificationDocument} for the evidence.
 */
@Entity
@Table(name = "authority_claim")
public class AuthorityClaim {

    public enum ClaimType { OWNER, AGENT_FOR_OWNER }

    public enum Status { PENDING, CONFIRMED, REJECTED, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(optional = false)
    @JoinColumn(name = "claimant_id", nullable = false)
    private User claimant;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private ClaimType claimType;

    @Column(name = "owner_name")
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @ManyToOne
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public AuthorityClaim() {}

    @PrePersist
    void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }
    public User getClaimant() { return claimant; }
    public void setClaimant(User claimant) { this.claimant = claimant; }
    public ClaimType getClaimType() { return claimType; }
    public void setClaimType(ClaimType claimType) { this.claimType = claimType; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public User getDecidedBy() { return decidedBy; }
    public void setDecidedBy(User decidedBy) { this.decidedBy = decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
