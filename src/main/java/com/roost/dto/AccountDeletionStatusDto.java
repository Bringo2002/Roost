package com.roost.dto;

import java.time.LocalDateTime;

/**
 * Response DTO exposing the current state of an account's deletion
 * lifecycle: whether a deletion is scheduled, has been cancelled, or
 * has never been requested.
 */
public class AccountDeletionStatusDto {

    /** One of {@code "scheduled"}, {@code "cancelled"}, or {@code "none"}. */
    private String status;

    /** When the deletion request was submitted. Null when status is "none". */
    private LocalDateTime deletionRequestedAt;

    /** When the account will be permanently deleted. Null when status is
     *  "none" or "cancelled". */
    private LocalDateTime deletionScheduledAt;

    /** Length of the grace period in days. Always present for client display. */
    private int gracePeriodDays;

    public AccountDeletionStatusDto() {}

    public AccountDeletionStatusDto(String status,
                                    LocalDateTime deletionRequestedAt,
                                    LocalDateTime deletionScheduledAt,
                                    int gracePeriodDays) {
        this.status = status;
        this.deletionRequestedAt = deletionRequestedAt;
        this.deletionScheduledAt = deletionScheduledAt;
        this.gracePeriodDays = gracePeriodDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public void setDeletionRequestedAt(LocalDateTime deletionRequestedAt) {
        this.deletionRequestedAt = deletionRequestedAt;
    }

    public LocalDateTime getDeletionScheduledAt() {
        return deletionScheduledAt;
    }

    public void setDeletionScheduledAt(LocalDateTime deletionScheduledAt) {
        this.deletionScheduledAt = deletionScheduledAt;
    }

    public int getGracePeriodDays() {
        return gracePeriodDays;
    }

    public void setGracePeriodDays(int gracePeriodDays) {
        this.gracePeriodDays = gracePeriodDays;
    }

    /** Creates a "scheduled" status from the given timestamps. */
    public static AccountDeletionStatusDto scheduled(LocalDateTime requestedAt,
                                                     LocalDateTime scheduledAt,
                                                     int gracePeriodDays) {
        return new AccountDeletionStatusDto("scheduled", requestedAt, scheduledAt, gracePeriodDays);
    }

    /** Creates a "cancelled" status. */
    public static AccountDeletionStatusDto cancelled(int gracePeriodDays) {
        return new AccountDeletionStatusDto("cancelled", null, null, gracePeriodDays);
    }

    /** Creates a "none" status (no pending deletion). */
    public static AccountDeletionStatusDto none(int gracePeriodDays) {
        return new AccountDeletionStatusDto("none", null, null, gracePeriodDays);
    }
}
