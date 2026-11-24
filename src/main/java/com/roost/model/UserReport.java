package com.roost.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A report one user files against another, kept for review. Chat messages
 * are end-to-end encrypted, so a report holds only the reason and the
 * reporter's optional note, never message content.
 */
@Entity
@Table(name = "user_reports")
public class UserReport {

    /** Why the reporter is reporting. Stored by name; the database checks the list. */
    public enum Reason {
        SPAM,
        HARASSMENT,
        INAPPROPRIATE_CONTENT,
        SCAM_OR_FRAUD,
        OTHER
    }

    /** Where a report is in review. */
    public enum Status {
        OPEN,
        RESOLVED
    }

    /** Longest note the database accepts. */
    public static final int MAX_NOTE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_id", nullable = false)
    private User reported;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason;

    @Column(length = MAX_NOTE_LENGTH)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected UserReport() {
    }

    /**
     * Creates an open report.
     *
     * @param reporter the user filing the report
     * @param reported the user being reported
     * @param reason   why they are being reported
     * @param note     optional free-text detail, at most {@value #MAX_NOTE_LENGTH} characters; may be null
     */
    public UserReport(User reporter, User reported, Reason reason, String note) {
        this.reporter = reporter;
        this.reported = reported;
        this.reason = reason;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public User getReporter() {
        return reporter;
    }

    public User getReported() {
        return reported;
    }

    public Reason getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
