package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.IdentityStatus;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.VerificationAuditEvent;
import com.roost.model.VerificationDocument;
import com.roost.repository.UserRepository;
import com.roost.repository.VerificationAuditEventRepository;
import com.roost.repository.VerificationDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Lets a lister submit the documents that prove who they are (national ID
 * front and back, or a passport). Files go to private storage; the database
 * keeps only the opaque key and a record of what was submitted.
 *
 * <p>A reviewer, not this service, decides whether documents are accepted.
 * Submitting only ever moves a person to {@link IdentityStatus#PENDING}.
 */
@Service
public class VerificationDocumentService {

    private static final Logger log = LoggerFactory.getLogger(VerificationDocumentService.class);

    /** Uploads allowed per user per hour, counting rejected files too. */
    static final int MAX_UPLOADS_PER_HOUR = 10;
    private static final Duration UPLOAD_WINDOW = Duration.ofHours(1);

    private final PrivateDocumentStorage storage;
    private final VerificationDocumentRepository documents;
    private final VerificationAuditEventRepository auditEvents;
    private final UserRepository users;
    private final RateLimiterService rateLimiter;

    public VerificationDocumentService(PrivateDocumentStorage storage,
                                       VerificationDocumentRepository documents,
                                       VerificationAuditEventRepository auditEvents,
                                       UserRepository users,
                                       RateLimiterService rateLimiter) {
        this.storage = storage;
        this.documents = documents;
        this.auditEvents = auditEvents;
        this.users = users;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Stores one identity document for a lister.
     *
     * <p>Sending a document of a type already on file replaces the earlier
     * one, unless a reviewer already accepted it. Once the lister's identity
     * is verified, documents can no longer be changed here.
     *
     * @param user the signed-in lister
     * @param type which document this is; must be an identity type
     * @param data the file bytes, checked by {@link VerificationFilePolicy}
     * @return the saved record
     * @throws ApiException 400 for a bad type or file, 403 if not a lister, 409 if the
     *                      document or identity is already accepted, 429 after too many
     *                      uploads, 503 if private storage is not available
     */
    @Transactional
    public VerificationDocument submitIdentityDocument(User user, VerificationDocument.DocType type, byte[] data) {
        if (user.getRole() != Role.LANDLORD) {
            throw ApiException.forbidden("Only listers can submit verification documents.");
        }
        if (type == null || !type.isIdentity()) {
            throw ApiException.badRequest("Choose an identity document: national ID front, national ID back, or passport.");
        }
        User owner = users.findById(user.getId())
                .orElseThrow(() -> ApiException.notFound("User not found."));
        if (owner.getIdentityStatus() == IdentityStatus.VERIFIED) {
            throw ApiException.conflict("Your identity is already verified.");
        }

        String uploadKey = "verification-upload:" + owner.getId();
        if (rateLimiter.isBlocked(uploadKey, MAX_UPLOADS_PER_HOUR, UPLOAD_WINDOW)) {
            throw ApiException.tooManyRequests("Too many uploads. Please wait a while before trying again.");
        }
        rateLimiter.record(uploadKey);

        VerificationFilePolicy.Accepted accepted = VerificationFilePolicy.check(data);

        List<VerificationDocument> active = new ArrayList<>(
                documents.findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(owner.getId()).stream()
                        .filter(d -> d.getDocType().isIdentity())
                        .toList());
        VerificationDocument replaced = active.stream()
                .filter(d -> d.getDocType() == type)
                .findFirst()
                .orElse(null);
        if (replaced != null && replaced.getStatus() == VerificationDocument.Status.ACCEPTED) {
            throw ApiException.conflict("This document was already accepted.");
        }

        String key = storage.store(data, accepted);
        VerificationDocument saved;
        try {
            VerificationDocument document = new VerificationDocument();
            document.setOwner(owner);
            document.setDocType(type);
            document.setStorageKey(key);
            document.setContentType(accepted.contentType());
            document.setSizeBytes(data.length);
            document.setSha256(sha256Hex(data));
            saved = documents.save(document);
            auditEvents.save(VerificationAuditEvent.of(owner, VerificationAuditEvent.Action.SUBMITTED, saved, null, null));

            if (replaced != null) {
                replaced.setDeletedAt(LocalDateTime.now());
                documents.save(replaced);
                auditEvents.save(VerificationAuditEvent.of(owner, VerificationAuditEvent.Action.DELETED, replaced, null, "replaced"));
                active.remove(replaced);
            }
            active.add(saved);

            if (isComplete(active)
                    && (owner.getIdentityStatus() == IdentityStatus.NOT_SUBMITTED
                    || owner.getIdentityStatus() == IdentityStatus.REJECTED)) {
                owner.setIdentityStatus(IdentityStatus.PENDING);
                users.save(owner);
            }
        } catch (RuntimeException e) {
            // The database work is rolled back, so remove the file we just stored.
            deleteQuietly(key);
            throw e;
        }

        if (replaced != null) {
            deleteQuietly(replaced.getStorageKey());
        }
        return saved;
    }

    /**
     * Lists the identity documents a lister has on file, newest first.
     *
     * @param userId the lister
     * @return documents that have not been deleted
     */
    @Transactional(readOnly = true)
    public List<VerificationDocument> listIdentityDocuments(Long userId) {
        return documents.findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(userId).stream()
                .filter(d -> d.getDocType().isIdentity())
                .toList();
    }

    /**
     * Where a person's identity verification stands, read fresh from the database.
     *
     * @param userId the person
     * @return their current status
     * @throws ApiException 404 if the user does not exist
     */
    @Transactional(readOnly = true)
    public IdentityStatus getIdentityStatus(Long userId) {
        return users.findById(userId)
                .map(User::getIdentityStatus)
                .orElseThrow(() -> ApiException.notFound("User not found."));
    }

    /** A passport alone, or both sides of a national ID, makes a complete set. */
    private static boolean isComplete(List<VerificationDocument> active) {
        boolean passport = false;
        boolean front = false;
        boolean back = false;
        for (VerificationDocument d : active) {
            switch (d.getDocType()) {
                case PASSPORT -> passport = true;
                case NATIONAL_ID_FRONT -> front = true;
                case NATIONAL_ID_BACK -> back = true;
                default -> { }
            }
        }
        return passport || (front && back);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // An orphaned file is harmless to the lister; the retention job can find it later.
            log.warn("Could not delete verification file {}: {}", key, e.getMessage());
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but not available", e);
        }
    }
}
