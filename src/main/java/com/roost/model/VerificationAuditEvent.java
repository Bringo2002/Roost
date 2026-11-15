package com.roost.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One line of the verification audit trail: a submission, a decision, or an
 * admin opening a document. Rows are never changed or removed; the database
 * refuses it, so this class has no setters.
 */
@Entity
@Table(name = "verification_audit_event")
public class VerificationAuditEvent {

    public enum Action {
        SUBMITTED, ACCEPTED, REJECTED, CONFIRMED, REVOKED, EXPIRED, DISPUTED, VIEWED, DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The person who acted; null for system actions such as expiry. */
    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Action action;

    @ManyToOne
    @JoinColumn(name = "document_id")
    private VerificationDocument document;

    @ManyToOne
    @JoinColumn(name = "claim_id")
    private AuthorityClaim claim;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected VerificationAuditEvent() {}

    /**
     * Records an event about a document, a claim, or both.
     *
     * @param actor who did it, or null for the system
     * @param action what happened
     * @param document the document involved, or null
     * @param claim the claim involved, or null
     * @param note an optional short explanation
     * @return the event, stamped with the current time
     */
    public static VerificationAuditEvent of(User actor, Action action, VerificationDocument document,
                                            AuthorityClaim claim, String note) {
        VerificationAuditEvent event = new VerificationAuditEvent();
        event.actor = actor;
        event.action = action;
        event.document = document;
        event.claim = claim;
        event.note = note;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public Long getId() { return id; }
    public User getActor() { return actor; }
    public Action getAction() { return action; }
    public VerificationDocument getDocument() { return document; }
    public AuthorityClaim getClaim() { return claim; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
