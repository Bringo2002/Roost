package com.roost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for initiating account deletion with a grace period.
 *
 * <p>The user must re-type their email as confirmation and provide their
 * password for re-authentication. Social-login users who have no known
 * password may supply a blank string — the service layer handles that
 * case separately. An optional free-text reason is collected for
 * product analytics but is not stored as PII.
 */
public class AccountDeletionRequestDto {

    @NotBlank(message = "Email confirmation is required")
    private String email;

    /** Current password for re-authentication. Social-login accounts that
     *  have no user-chosen password may omit this (the field is still
     *  required by Bean Validation, but the service tolerates a mismatch
     *  when the user signed up via Google/Apple). */
    @NotBlank(message = "Password is required for re-authentication")
    private String password;

    /** Optional reason the user is leaving — capped to 500 characters. */
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;

    public AccountDeletionRequestDto() {}

    public AccountDeletionRequestDto(String email, String password, String reason) {
        this.email = email;
        this.password = password;
        this.reason = reason;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
