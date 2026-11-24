package com.roost.service;

import com.roost.dto.AccountDeletionStatusDto;
import com.roost.exception.ApiException;
import com.roost.model.Message;
import com.roost.model.MoveInInspection;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.model.VerificationDocument;
import com.roost.repository.ApplicationRepository;
import com.roost.repository.ChatVisibilityRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.MoveInInspectionRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.UserRepository;
import com.roost.repository.VerificationDocumentRepository;
import com.roost.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Self-service account deletion with a configurable grace period.
 *
 * <h3>Two-phase deletion model</h3>
 * <ol>
 *   <li><strong>Request</strong> — the user confirms their email, re-authenticates
 *       with their password (accounts created through Google or Apple sign-in,
 *       which have no password the user knows, skip this), and optionally
 *       provides a reason. The account is
 *       marked for deletion {@value #GRACE_PERIOD_DAYS} days in the future but
 *       remains fully functional during the grace window.</li>
 *   <li><strong>Execution</strong> — a nightly scheduled task
 *       ({@link AccountDeletionScheduledTask}) picks up expired grace periods
 *       and runs the irreversible anonymization: personal data is wiped, listings
 *       retired, applications and messages hard-deleted, verification documents
 *       scrubbed, uploaded files queued for deletion ({@link FileDeletionQueue}),
 *       and the account disabled.</li>
 * </ol>
 *
 * <p>The account is soft-deleted rather than removed: the user row is kept so
 * the foreign keys held by reviews, reports, inspections and the verification
 * audit trail stay valid. Because
 * the email changes on execution, tokens issued before deletion no longer
 * resolve to a user and are rejected by the JWT filter.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    static final String DELETED_NAME = "Deleted user";
    static final String DELETED_EMAIL_DOMAIN = "deleted.roost.invalid";

    /** Number of days between a deletion request and its execution. */
    static final int GRACE_PERIOD_DAYS = 14;

    private static final String PUBLISHED = "PUBLISHED";
    private static final String DRAFT = "DRAFT";

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final MessageRepository messageRepository;
    private final ChatVisibilityRepository chatVisibilityRepository;
    private final MoveInInspectionRepository moveInInspectionRepository;
    private final FileDeletionQueue fileDeletionQueue;

    public AccountDeletionService(UserRepository userRepository,
                                  PropertyRepository propertyRepository,
                                  ApplicationRepository applicationRepository,
                                  PasswordEncoder passwordEncoder,
                                  VerificationDocumentRepository verificationDocumentRepository,
                                  MessageRepository messageRepository,
                                  ChatVisibilityRepository chatVisibilityRepository,
                                  MoveInInspectionRepository moveInInspectionRepository,
                                  FileDeletionQueue fileDeletionQueue) {
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationDocumentRepository = verificationDocumentRepository;
        this.messageRepository = messageRepository;
        this.chatVisibilityRepository = chatVisibilityRepository;
        this.moveInInspectionRepository = moveInInspectionRepository;
        this.fileDeletionQueue = fileDeletionQueue;
    }

    // ── Phase 1: Request / Cancel / Status ──────────────────────────────

    /**
     * Initiates account deletion with a grace period.
     *
     * <p>The user must confirm by typing their email. Password accounts must
     * also re-authenticate with their password; accounts created through
     * Google or Apple sign-in have no password the user knows, so only the
     * email confirmation applies to them. An optional reason is stored for
     * analytics.
     * The account continues to function during the grace period.
     *
     * @param user     the authenticated user requesting deletion
     * @param email    the email the user typed to confirm
     * @param password the user's current password for re-authentication;
     *                 ignored for social-login accounts
     * @param reason   optional free-text reason (may be null)
     * @return deletion status with the scheduled execution date
     * @throws ApiException 400 if email/password is wrong; 409 if already scheduled
     */
    @Transactional
    public AccountDeletionStatusDto requestDeletion(User user, String email,
                                                    String password, String reason) {
        if (user.isDeletionPending()) {
            throw ApiException.conflict("Account deletion is already scheduled for "
                    + user.getDeletionScheduledAt());
        }

        validateConfirmationEmail(user, email);
        if (requiresPasswordReauthentication(user)) {
            validatePassword(user, password);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledAt = now.plusDays(GRACE_PERIOD_DAYS);

        user.setDeletionRequestedAt(now);
        user.setDeletionScheduledAt(scheduledAt);
        user.setDeletionReason(reason);
        userRepository.save(user);

        log.info("Account deletion requested for user {} (id={}), scheduled for {}",
                user.getEmail(), user.getId(), scheduledAt);

        return AccountDeletionStatusDto.scheduled(now, scheduledAt, GRACE_PERIOD_DAYS);
    }

    /**
     * Cancels a pending account deletion during the grace period.
     *
     * @param user the authenticated user cancelling deletion
     * @return deletion status confirming cancellation
     * @throws ApiException 400 if no deletion is pending
     */
    @Transactional
    public AccountDeletionStatusDto cancelDeletion(User user) {
        if (!user.isDeletionPending()) {
            throw ApiException.badRequest("No pending account deletion to cancel");
        }

        log.info("Account deletion cancelled for user {} (id={}), was scheduled for {}",
                user.getEmail(), user.getId(), user.getDeletionScheduledAt());

        user.setDeletionRequestedAt(null);
        user.setDeletionScheduledAt(null);
        user.setDeletionReason(null);
        userRepository.save(user);

        return AccountDeletionStatusDto.cancelled(GRACE_PERIOD_DAYS);
    }

    /**
     * Returns the current deletion status for the authenticated user.
     *
     * @param user the authenticated user
     * @return deletion status ("scheduled" or "none")
     */
    public AccountDeletionStatusDto getDeletionStatus(User user) {
        if (user.isDeletionPending()) {
            return AccountDeletionStatusDto.scheduled(
                    user.getDeletionRequestedAt(),
                    user.getDeletionScheduledAt(),
                    GRACE_PERIOD_DAYS);
        }
        return AccountDeletionStatusDto.none(GRACE_PERIOD_DAYS);
    }

    // ── Phase 2: Scheduled execution ────────────────────────────────────

    /**
     * Executes all deletions whose grace period has expired. Called by
     * {@link AccountDeletionScheduledTask} on a nightly cron schedule.
     *
     * @return the number of accounts processed
     */
    @Transactional
    public int executeScheduledDeletions() {
        List<User> expired = userRepository.findByDeletionScheduledAtBeforeAndEnabledTrue(
                LocalDateTime.now());

        for (User user : expired) {
            log.info("Executing scheduled deletion for user {} (id={})",
                    user.getEmail(), user.getId());
            executeIrreversibleDeletion(user);
        }

        if (!expired.isEmpty()) {
            log.info("Processed {} scheduled account deletion(s)", expired.size());
        }

        return expired.size();
    }

    /**
     * Runs the irreversible deletion sequence on a single user: queues their
     * uploaded files for deletion, deletes applications and messages, retires
     * listings, scrubs verification documents, anonymizes the user row, and
     * disables the account. This is the internal execution engine — not
     * directly exposed to controllers.
     *
     * @param user the user to permanently delete
     */
    @Transactional
    public void executeIrreversibleDeletion(User user) {
        erase(user);
    }

    /**
     * Legacy instant-deletion entry point. Validates the confirmation email,
     * then runs the full irreversible deletion immediately (no grace period).
     *
     * <p>Retained for backward compatibility with admin tooling and the
     * existing test suite. The public API now routes through
     * {@link #requestDeletion} instead.
     *
     * @param user         the authenticated user to delete
     * @param confirmEmail the email the user typed to confirm; compared to the
     *                     account email after normalization
     * @throws ApiException 400 when the confirmation is missing or does not match
     */
    @Transactional
    public void deleteAccount(User user, String confirmEmail) {
        validateConfirmationEmail(user, confirmEmail);
        erase(user);
    }

    // ── Validation helpers ──────────────────────────────────────────────

    /**
     * Validates that the confirmation email matches the user's account email.
     *
     * @param user         the user whose email to check against
     * @param confirmEmail the email the user typed to confirm
     * @throws ApiException 400 when the confirmation is missing or does not match
     */
    private void validateConfirmationEmail(User user, String confirmEmail) {
        if (confirmEmail == null || confirmEmail.isBlank()) {
            throw ApiException.badRequest("Email confirmation is required");
        }
        if (!EmailNormalizer.normalize(confirmEmail).equals(user.getEmail())) {
            throw ApiException.badRequest("Email does not match this account");
        }
    }

    /**
     * Whether this account must supply its password to request deletion.
     *
     * <p>Only Google and Apple sign-in mark an account email-verified, and
     * both create it with a random password nobody knows, so asking those
     * users for it would make deletion impossible. Password signups are
     * always created unverified, and a social login never attaches itself to
     * an unverified account.
     */
    private boolean requiresPasswordReauthentication(User user) {
        return !user.isEmailVerified();
    }

    /**
     * Validates the user's password for re-authentication.
     *
     * @param user     the user to authenticate
     * @param password the password the user typed
     * @throws ApiException 400 when the password is missing or does not match
     */
    private void validatePassword(User user, String password) {
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("Password is required for re-authentication");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw ApiException.badRequest("Password is incorrect");
        }
    }

    // ── Internal deletion machinery ─────────────────────────────────────

    /**
     * The erasure sequence shared by the scheduled and the legacy instant
     * paths. Order matters: files are queued before the listing references
     * to them are cleared, and the tenant's name is copied onto their
     * inspections before the account is renamed.
     */
    private void erase(User user) {
        List<Property> owned = propertyRepository.findByOwner(user);
        queueUploadedFiles(user, owned);
        deleteApplications(user, owned);
        retireListings(owned);
        eraseVerificationDocuments(user);
        eraseMessages(user);
        keepTenantNameOnInspections(user);
        anonymize(user);
        userRepository.save(user);
    }

    /**
     * Queues the account's profile photo and every photo, document and video
     * on the listings it owns. They are deleted after the retention period,
     * once nothing else still uses the same stored file.
     */
    private void queueUploadedFiles(User user, List<Property> owned) {
        fileDeletionQueue.queuePublicUrl(user.getAvatarUrl());
        for (Property property : owned) {
            fileDeletionQueue.queuePublicUrl(property.getImageUrl());
            if (property.getImageUrls() != null) {
                property.getImageUrls().forEach(fileDeletionQueue::queuePublicUrl);
            }
            if (property.getDocumentUrls() != null) {
                property.getDocumentUrls().forEach(fileDeletionQueue::queuePublicUrl);
            }
            fileDeletionQueue.queuePublicUrl(property.getVideoUrl());
        }
    }

    /**
     * Queues every verification document file (IDs, deeds, utility bills) and
     * clears the AI analysis, which can contain details read off the document.
     * The rows stay, marked deleted: the append-only audit trail refers to them.
     */
    private void eraseVerificationDocuments(User user) {
        List<VerificationDocument> documents = verificationDocumentRepository.findByOwnerId(user.getId());
        if (documents.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (VerificationDocument document : documents) {
            fileDeletionQueue.queuePrivateKey(document.getStorageKey());
            document.setAiOpinion(null);
            if (document.getDeletedAt() == null) {
                document.setDeletedAt(now);
            }
        }
        verificationDocumentRepository.saveAll(documents);
    }

    /**
     * Deletes every message the account sent or received, with its reactions,
     * queues the attachment files, and removes the account's chat settings.
     * The messages are end-to-end encrypted and the account's keys are erased
     * here, so the other person could no longer read them anyway.
     */
    private void eraseMessages(User user) {
        List<Message> messages = messageRepository.findAllInvolving(user);
        for (Message message : messages) {
            fileDeletionQueue.queuePublicKey(message.getAttachmentStorageKey());
        }
        messageRepository.deleteAll(messages);
        chatVisibilityRepository.deleteAllInvolving(user);
    }

    /**
     * An inspection is evidence the landlord may need later, so it must keep
     * naming the tenant. New ones record the name when they are created; this
     * fills it in for any that do not have one before the account is renamed.
     */
    private void keepTenantNameOnInspections(User user) {
        List<MoveInInspection> inspections = moveInInspectionRepository.findByTenantOrderByCreatedAtDesc(user);
        List<MoveInInspection> missingName = inspections.stream()
                .filter(inspection -> inspection.getTenantName() == null || inspection.getTenantName().isBlank())
                .toList();
        if (missingName.isEmpty()) {
            return;
        }
        missingName.forEach(inspection -> inspection.setTenantName(user.getName()));
        moveInInspectionRepository.saveAll(missingName);
    }

    /**
     * Deletes the applications this account is part of. They carry a full
     * name, national ID, employment status and income, so they are removed
     * outright rather than anonymized: both the ones the user submitted and
     * the ones other people sent to the user's listings, which would
     * otherwise stay attached to a listing nobody can reach any more.
     */
    private void deleteApplications(User user, List<Property> owned) {
        applicationRepository.deleteAll(applicationRepository.findByApplicantOrderByCreatedAtDesc(user));
        owned.forEach(applicationRepository::deleteByProperty);
    }

    /**
     * Takes the owner's live listings out of the public feed and strips the
     * personal details from every listing they own. The rows stay, because
     * events, rank scores and inspections still point at them, but nothing
     * that identifies the owner, the caretaker or the people on site remains.
     */
    private void retireListings(List<Property> owned) {
        if (owned.isEmpty()) {
            return;
        }
        for (Property property : owned) {
            if (PUBLISHED.equals(property.getStatus())) {
                property.setStatus(DRAFT);
            }
            scrubPersonalData(property);
        }
        propertyRepository.saveAll(owned);
    }

    /**
     * Clears contact names and phone numbers, the endorsement link, and every
     * photo, document and video reference. The stored files themselves are
     * not deleted here: {@link #queueUploadedFiles} queues them, and the
     * deletion job checks that no other listing still uses the same
     * de-duplicated file before removing it.
     */
    private void scrubPersonalData(Property property) {
        property.setLandlordName(null);
        property.setLandlordPhone(null);
        property.setLandlordId(null);
        property.setOwnerVerifyName(null);
        property.setOwnerVerifyPhone(null);
        property.setCaretakerName(null);
        property.setCaretakerPhone(null);
        property.setEndorsementToken(null);
        property.setImageUrl(null);
        property.setImageUrls(new ArrayList<>());
        property.setDocumentUrls(new ArrayList<>());
        property.setVideoUrl(null);
    }

    private void anonymize(User user) {
        user.setName(DELETED_NAME);
        user.setEmail("deleted-" + user.getId() + "@" + DELETED_EMAIL_DOMAIN);
        user.setPhone(null);
        user.setPhoneVerified(false);
        user.setEmailVerified(false);
        user.setAvatarUrl(null);
        user.setPublicKey(null);
        user.setDeviceToken(null);
        user.setSavedPropertyIds(new HashSet<>());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDeletionRequestedAt(null);
        user.setDeletionScheduledAt(null);
        user.setEnabled(false);
    }
}
