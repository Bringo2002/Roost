package com.roost.exception;

/**
 * One reason a listing cannot be published.
 *
 * @param field   the listing input the problem is about (e.g. {@code title})
 * @param code    stable, machine-readable identifier that clients can branch on
 * @param message user-facing explanation of what to fix
 */
public record PublicationViolation(String field, String code, String message) {
}
