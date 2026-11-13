package com.roost.service;

import com.roost.dto.AccountDeletionStatusDto;
import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.ApplicationRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the grace-period account deletion flow: requesting deletion,
 * cancelling during the grace window, querying status, and executing
 * scheduled deletions.
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionGracePeriodServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountDeletionService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setPhone("+254700000000");
        user.setPhoneVerified(true);
        user.setEmailVerified(true);
        user.setPassword("stored-hash");
        user.setRole(Role.LANDLORD);
        user.setPublicKey("public-key");
        user.setDeviceToken("fcm-token");
        user.setAvatarUrl("https://cdn.example.com/ada.png");
        user.setSavedPropertyIds(new HashSet<>(Set.of(1L, 2L)));
    }

    // ── Request deletion ────────────────────────────────────────────────

    @Nested
    @DisplayName("requestDeletion")
    class RequestDeletion {

        @Test
        @DisplayName("sets scheduled dates and returns 'scheduled' status")
        void setsDatesAndReturnsScheduled() {
            when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

            AccountDeletionStatusDto result = service.requestDeletion(
                    user, "ada@example.com", "correct-password", "Moving away");

            assertEquals("scheduled", result.getStatus());
            assertNotNull(result.getDeletionRequestedAt());
            assertNotNull(result.getDeletionScheduledAt());
            assertEquals(AccountDeletionService.GRACE_PERIOD_DAYS, result.getGracePeriodDays());

            // User entity fields should be set
            assertNotNull(user.getDeletionRequestedAt());
            assertNotNull(user.getDeletionScheduledAt());
            assertEquals("Moving away", user.getDeletionReason());
            assertTrue(user.isEnabled()); // Still active during grace period
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("schedules deletion exactly GRACE_PERIOD_DAYS in the future")
        void schedulesCorrectGracePeriod() {
            when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);

            LocalDateTime before = LocalDateTime.now();
            service.requestDeletion(user, "ada@example.com", "password", null);
            LocalDateTime after = LocalDateTime.now();

            LocalDateTime scheduled = user.getDeletionScheduledAt();
            assertTrue(scheduled.isAfter(before.plusDays(AccountDeletionService.GRACE_PERIOD_DAYS).minusSeconds(1)));
            assertTrue(scheduled.isBefore(after.plusDays(AccountDeletionService.GRACE_PERIOD_DAYS).plusSeconds(1)));
        }

        @Test
        @DisplayName("rejects wrong password without scheduling deletion")
        void rejectsWrongPassword() {
            when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

            ApiException ex = assertThrows(ApiException.class,
                    () -> service.requestDeletion(user, "ada@example.com", "wrong", null));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertNull(user.getDeletionScheduledAt());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects mismatched email without scheduling deletion")
        void rejectsWrongEmail() {
            ApiException ex = assertThrows(ApiException.class,
                    () -> service.requestDeletion(user, "someone@else.com", "password", null));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertNull(user.getDeletionScheduledAt());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects missing email or password")
        void rejectsMissingFields() {
            ApiException missingEmail = assertThrows(ApiException.class,
                    () -> service.requestDeletion(user, null, "password", null));
            assertEquals(HttpStatus.BAD_REQUEST, missingEmail.getStatus());

            ApiException blankEmail = assertThrows(ApiException.class,
                    () -> service.requestDeletion(user, "  ", "password", null));
            assertEquals(HttpStatus.BAD_REQUEST, blankEmail.getStatus());

            ApiException missingPassword = assertThrows(ApiException.class,
                    () -> service.requestDeletion(user, "ada@example.com", null, null));
            assertEquals(HttpStatus.BAD_REQUEST, missingPassword.getStatus());

            ApiException blankPassword = assertThrows(ApiException.class,
                    () -> service.requestDeletion(user, "ada@example.com", "  ", null));
            assertEquals(HttpStatus.BAD_REQUEST, blankPassword.getStatus());
        }

        @Test
        @DisplayName("rejects a second request when deletion is already scheduled")
        void rejectsAlreadyPendingDeletion() {
            user.setDeletionScheduledAt(LocalDateTime.now().plusDays(10));

            ApiException ex = assertThrows(ApiException.class,
                    () -> service.requestDeletion(user, "ada@example.com", "password", null));

            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        }

        @Test
        @DisplayName("accepts a null reason without error")
        void acceptsNullReason() {
            when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);

            AccountDeletionStatusDto result = service.requestDeletion(
                    user, "ada@example.com", "password", null);

            assertEquals("scheduled", result.getStatus());
            assertNull(user.getDeletionReason());
        }
    }

    // ── Cancel deletion ─────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelDeletion")
    class CancelDeletion {

        @Test
        @DisplayName("clears scheduling fields and returns 'cancelled' status")
        void clearsDatesAndReturnsCancelled() {
            user.setDeletionRequestedAt(LocalDateTime.now().minusDays(3));
            user.setDeletionScheduledAt(LocalDateTime.now().plusDays(11));
            user.setDeletionReason("Testing");

            AccountDeletionStatusDto result = service.cancelDeletion(user);

            assertEquals("cancelled", result.getStatus());
            assertNull(result.getDeletionRequestedAt());
            assertNull(result.getDeletionScheduledAt());
            assertNull(user.getDeletionRequestedAt());
            assertNull(user.getDeletionScheduledAt());
            assertNull(user.getDeletionReason());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("rejects cancellation when no deletion is pending")
        void rejectsWhenNoPendingDeletion() {
            ApiException ex = assertThrows(ApiException.class,
                    () -> service.cancelDeletion(user));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            verify(userRepository, never()).save(any());
        }
    }

    // ── Deletion status ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getDeletionStatus")
    class GetDeletionStatus {

        @Test
        @DisplayName("returns 'scheduled' when a deletion is pending")
        void returnsScheduledWhenPending() {
            LocalDateTime requested = LocalDateTime.now().minusDays(3);
            LocalDateTime scheduled = LocalDateTime.now().plusDays(11);
            user.setDeletionRequestedAt(requested);
            user.setDeletionScheduledAt(scheduled);

            AccountDeletionStatusDto result = service.getDeletionStatus(user);

            assertEquals("scheduled", result.getStatus());
            assertEquals(requested, result.getDeletionRequestedAt());
            assertEquals(scheduled, result.getDeletionScheduledAt());
            assertEquals(AccountDeletionService.GRACE_PERIOD_DAYS, result.getGracePeriodDays());
        }

        @Test
        @DisplayName("returns 'none' when no deletion is pending")
        void returnsNoneWhenNotPending() {
            AccountDeletionStatusDto result = service.getDeletionStatus(user);

            assertEquals("none", result.getStatus());
            assertNull(result.getDeletionRequestedAt());
            assertNull(result.getDeletionScheduledAt());
        }
    }

    // ── Scheduled execution ─────────────────────────────────────────────

    @Nested
    @DisplayName("executeScheduledDeletions")
    class ExecuteScheduledDeletions {

        @Test
        @DisplayName("processes all users whose grace period has expired")
        void processesExpiredGracePeriods() {
            User expiredUser = new User();
            expiredUser.setId(99L);
            expiredUser.setName("Expired User");
            expiredUser.setEmail("expired@example.com");
            expiredUser.setPassword("hash");
            expiredUser.setDeletionScheduledAt(LocalDateTime.now().minusDays(1));

            when(userRepository.findByDeletionScheduledAtBeforeAndEnabledTrue(any()))
                    .thenReturn(List.of(expiredUser));
            when(propertyRepository.findByOwner(expiredUser)).thenReturn(List.of());
            when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");

            int count = service.executeScheduledDeletions();

            assertEquals(1, count);
            assertFalse(expiredUser.isEnabled());
            assertEquals(AccountDeletionService.DELETED_NAME, expiredUser.getName());
            verify(userRepository).save(expiredUser);
        }

        @Test
        @DisplayName("returns zero and does nothing when no grace periods have expired")
        void skipsNotYetExpired() {
            when(userRepository.findByDeletionScheduledAtBeforeAndEnabledTrue(any()))
                    .thenReturn(List.of());

            int count = service.executeScheduledDeletions();

            assertEquals(0, count);
        }
    }
}
