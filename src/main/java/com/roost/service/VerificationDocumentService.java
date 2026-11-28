package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.IdentityStatus;
import com.roost.model.NotificationType;
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
import java.util.Map;

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
    private final FirebasePushService firebasePushService;

    public VerificationDocumentService(PrivateDocumentStorage storage,
                                       VerificationDocumentRepository documents,
                                       VerificationAuditEventRepository auditEvents,
                                       UserRepository users,
                                       RateLimiterService rateLimiter,
                                       FirebasePushService firebasePushService) {
        this.storage = storage;
        this.documents = documents;
        this.auditEvents = auditEvents;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.firebasePushService = firebasePushService;
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

    /** A file read for review, with the type to serve it as. */
    public record ReviewFile(byte[] data, String contentType) {}

    /** Longest reason a reviewer can give; matches the database columns. */
    static final int MAX_REJECT_REASON = 1000;

    /**
     * Listers whose identity documents are waiting for a reviewer.
     *
     * @return listers at the pending stage, oldest account first
     */
    @Transactional(readOnly = true)
    public List<User> listPendingIdentities() {
        return users.findByIdentityStatusOrderByIdAsc(IdentityStatus.PENDING);
    }

    /**
     * Reads a stored document for a reviewer and records that they looked.
     *
     * <p>The view is written down only after the file was read and before it
     * is returned, so a file never reaches a reviewer without a record.
     *
     * @param reviewer the admin viewing the file
     * @param documentId the document to read
     * @return the file bytes and the type to serve them as
     * @throws ApiException 404 if the document does not exist or was deleted,
     *                      503 if private storage is not available
     */
    @Transactional
    public ReviewFile viewDocument(User reviewer, Long documentId) {
        VerificationDocument document = documents.findById(documentId)
                .filter(d -> d.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Document not found."));
        byte[] data = storage.load(document.getStorageKey());
        auditEvents.save(VerificationAuditEvent.of(
                reviewer, VerificationAuditEvent.Action.VIEWED, document, null, null));
        return new ReviewFile(data, document.getContentType());
    }

    /**
     * Accepts a lister's identity documents and marks their identity verified.
     *
     * <p>Every identity document on file must be a freshly submitted one. If a
     * document was rejected earlier and not replaced, accepting would quietly
     * overturn that decision, so the reviewer has to reject again or wait for
     * the replacement.
     *
     * @param reviewer the admin deciding
     * @param userId the lister whose documents are reviewed
     * @throws ApiException 403 for your own documents, 404 if no such user, 409 if the lister is
     *                      not waiting for review, the set is incomplete, or a document was
     *                      rejected earlier and not replaced
     */
    @Transactional
    public void acceptIdentity(User reviewer, Long userId) {
        User owner = pendingOwner(reviewer, userId);
        List<VerificationDocument> active = listIdentityDocuments(owner.getId());
        if (!isComplete(active)) {
            throw ApiException.conflict("The documents on file are not a complete set.");
        }
        if (active.stream().anyMatch(d -> d.getStatus() != VerificationDocument.Status.SUBMITTED)) {
            throw ApiException.conflict("Some documents were rejected earlier and have not been replaced.");
        }
        LocalDateTime now = LocalDateTime.now();
        for (VerificationDocument document : active) {
            document.setStatus(VerificationDocument.Status.ACCEPTED);
            document.setReviewedBy(reviewer);
            document.setReviewedAt(now);
            document.setRejectReason(null);
            documents.save(document);
            auditEvents.save(VerificationAuditEvent.of(
                    reviewer, VerificationAuditEvent.Action.ACCEPTED, document, null, null));
        }
        owner.setIdentityStatus(IdentityStatus.VERIFIED);
        users.save(owner);
        firebasePushService.sendToUser(
                owner,
                "Identity verified",
                "We've reviewed your identity documents and everything checks out. You're now verified on Roost — this helps build trust with tenants browsing your listings.",
                Map.of("type", NotificationType.IDENTITY_VERIFIED.key())
        );
    }

    /**
     * Rejects a lister's submitted identity documents, with a reason the
     * lister will see. The lister can then submit new ones.
     *
     * @param reviewer the admin deciding
     * @param userId the lister whose documents are reviewed
     * @param reason what to fix; required, at most {@value #MAX_REJECT_REASON} characters
     * @throws ApiException 400 for a missing or too long reason, 403 for your own documents,
     *                      404 if no such user, 409 if the lister is not waiting for review
     */
    @Transactional
    public void rejectIdentity(User reviewer, Long userId, String reason) {
        String cleaned = reason == null ? "" : reason.trim();
        if (cleaned.isEmpty()) {
            throw ApiException.badRequest("Tell the lister what to fix.");
        }
        if (cleaned.length() > MAX_REJECT_REASON) {
            throw ApiException.badRequest("The reason is too long. The limit is " + MAX_REJECT_REASON + " characters.");
        }
        User owner = pendingOwner(reviewer, userId);
        LocalDateTime now = LocalDateTime.now();
        for (VerificationDocument document : listIdentityDocuments(owner.getId())) {
            if (document.getStatus() != VerificationDocument.Status.SUBMITTED) {
                continue;
            }
            document.setStatus(VerificationDocument.Status.REJECTED);
            document.setReviewedBy(reviewer);
            document.setReviewedAt(now);
            document.setRejectReason(cleaned);
            documents.save(document);
            auditEvents.save(VerificationAuditEvent.of(
                    reviewer, VerificationAuditEvent.Action.REJECTED, document, null, cleaned));
        }
        owner.setIdentityStatus(IdentityStatus.REJECTED);
        users.save(owner);
        firebasePushService.sendToUser(
                owner,
                "Quick update on your documents",
                "We reviewed your identity documents but weren't able to verify them — " + cleaned + ". You can submit new ones anytime and we'll take another look right away.",
                Map.of("type", NotificationType.IDENTITY_REJECTED.key())
        );
    }

    /** The lister under review, checked to be someone else and still waiting. */
    private User pendingOwner(User reviewer, Long userId) {
        if (reviewer.getId().equals(userId)) {
            throw ApiException.forbidden("You can't review your own documents.");
        }
        User owner = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found."));
        if (owner.getIdentityStatus() != IdentityStatus.PENDING) {
            throw ApiException.conflict("This person's documents are not waiting for review.");
        }
        return owner;
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
