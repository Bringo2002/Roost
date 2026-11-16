package com.roost.dto;

import java.util.List;

/**
 * A lister's identity verification at a glance.
 *
 * @param status {@code NOT_SUBMITTED}, {@code PENDING}, {@code VERIFIED} or {@code REJECTED}
 * @param documents the identity documents on file, newest first
 */
public record IdentityVerificationDto(String status, List<VerificationDocumentDto> documents) {
}
