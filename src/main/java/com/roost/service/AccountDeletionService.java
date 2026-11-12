package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.ApplicationRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.UserRepository;
import com.roost.util.EmailNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Self-service account deletion.
 *
 * <p>The account is soft-deleted rather than removed: personal data is wiped
 * and the user disabled, but the row is kept so the foreign keys held by
 * messages, reviews, applications and reports stay valid. Because the email
 * changes, tokens issued before deletion no longer resolve to a user and are
 * rejected by the JWT filter.
 */
@Service
public class AccountDeletionService {

    static final String DELETED_NAME = "Deleted user";
    static final String DELETED_EMAIL_DOMAIN = "deleted.roost.invalid";

    private static final String PUBLISHED = "PUBLISHED";
    private static final String DRAFT = "DRAFT";

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountDeletionService(UserRepository userRepository,
                                  PropertyRepository propertyRepository,
                                  ApplicationRepository applicationRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Deletes the given user's account after they confirm it by typing the
     * account's email address.
     *
     * @param user         the authenticated user to delete
     * @param confirmEmail the email the user typed to confirm; compared to the
     *                     account email after normalization
     * @throws ApiException 400 when the confirmation is missing or does not match
     */
    @Transactional
    public void deleteAccount(User user, String confirmEmail) {
        if (confirmEmail == null || confirmEmail.isBlank()) {
            throw ApiException.badRequest("Email confirmation is required");
        }
        if (!EmailNormalizer.normalize(confirmEmail).equals(user.getEmail())) {
            throw ApiException.badRequest("Email does not match this account");
        }

        List<Property> owned = propertyRepository.findByOwner(user);
        deleteApplications(user, owned);
        unpublishListings(owned);
        anonymize(user);
        userRepository.save(user);
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

    /** Takes the owner's live listings out of the public feed. */
    private void unpublishListings(List<Property> owned) {
        List<Property> published = owned.stream()
                .filter(property -> PUBLISHED.equals(property.getStatus()))
                .toList();
        published.forEach(property -> property.setStatus(DRAFT));
        propertyRepository.saveAll(published);
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
        user.setEnabled(false);
    }
}
