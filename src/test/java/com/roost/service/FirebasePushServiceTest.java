package com.roost.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link FirebasePushService}. Uses a mock {@link FcmSender}
 * so no live Firebase project is needed. Each test creates the service via
 * the package-private constructor that lets us control the {@code configured}
 * flag directly.
 */
@ExtendWith(MockitoExtension.class)
class FirebasePushServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FcmSender fcmSender;

    private FirebasePushService service;
    private User recipient;

    @BeforeEach
    void setUp() {
        // configured=true so sends are attempted by default
        service = new FirebasePushService(userRepository, fcmSender, true);

        recipient = new User();
        recipient.setId(1L);
        recipient.setName("Test User");
        recipient.setRole(Role.TENANT);
        recipient.setEnabled(true);
        recipient.setDeviceToken("valid-fcm-token");
    }

    // ── No-op guards ────────────────────────────────────────────────

    @Test
    @DisplayName("unconfigured service is a complete no-op")
    void unconfiguredIsNoOp() {
        FirebasePushService unconfigured = new FirebasePushService(userRepository, fcmSender, false);

        unconfigured.sendToUser(recipient, "Title", "Body", Map.of("type", "chat"));

        verifyNoInteractions(fcmSender);
    }

    @Test
    @DisplayName("null recipient is a no-op")
    void nullRecipientIsNoOp() {
        service.sendToUser(null, "Title", "Body", Map.of("type", "chat"));

        verifyNoInteractions(fcmSender);
    }

    @Test
    @DisplayName("null device token is a no-op")
    void nullTokenIsNoOp() {
        recipient.setDeviceToken(null);

        service.sendToUser(recipient, "Title", "Body", Map.of("type", "chat"));

        verifyNoInteractions(fcmSender);
    }

    @Test
    @DisplayName("blank device token is a no-op")
    void blankTokenIsNoOp() {
        recipient.setDeviceToken("   ");

        service.sendToUser(recipient, "Title", "Body", Map.of("type", "chat"));

        verifyNoInteractions(fcmSender);
    }

    // ── Happy path ──────────────────────────────────────────────────

    @Test
    @DisplayName("valid send delegates to FcmSender")
    void validSendDelegatesToFcmSender() {
        service.sendToUser(recipient, "Hello", "World", Map.of("type", "chat"));

        verify(fcmSender).sendAsync(any(Message.class), any(Runnable.class), any());
    }

    @Test
    @DisplayName("sendToUser convenience method delegates to 4-arg version")
    void convenienceMethodDelegates() {
        service.sendToUser(recipient, "Hello", "World");

        verify(fcmSender).sendAsync(any(Message.class), any(Runnable.class), any());
    }

    // ── Rate limiting ───────────────────────────────────────────────

    @Test
    @DisplayName("rate limiting: 11th push in a minute is dropped")
    void rateLimitDropsExcessPushes() {
        // Send MAX_PUSHES_PER_MINUTE pushes — all should go through
        for (int i = 0; i < FirebasePushService.MAX_PUSHES_PER_MINUTE; i++) {
            service.sendToUser(recipient, "Title", "Body " + i, Map.of("type", "chat"));
        }

        // The 11th should be throttled
        service.sendToUser(recipient, "Title", "Body overflow", Map.of("type", "chat"));

        // FcmSender should have been called exactly MAX times, not MAX+1
        verify(fcmSender, org.mockito.Mockito.times(FirebasePushService.MAX_PUSHES_PER_MINUTE))
                .sendAsync(any(Message.class), any(Runnable.class), any());
    }

    @Test
    @DisplayName("rate limit resets after resetPushCounters()")
    void rateLimitResetsAfterClear() {
        // Exhaust the limit
        for (int i = 0; i < FirebasePushService.MAX_PUSHES_PER_MINUTE; i++) {
            service.sendToUser(recipient, "Title", "Body " + i, Map.of("type", "chat"));
        }

        // Reset counters (simulates the @Scheduled 60s tick)
        service.resetPushCounters();

        // Should be able to send again
        service.sendToUser(recipient, "Title", "After reset", Map.of("type", "chat"));

        verify(fcmSender, org.mockito.Mockito.times(FirebasePushService.MAX_PUSHES_PER_MINUTE + 1))
                .sendAsync(any(Message.class), any(Runnable.class), any());
    }

    // ── Stale token eviction ────────────────────────────────────────

    @Test
    @DisplayName("UNREGISTERED error evicts the device token")
    void unregisteredErrorEvictsToken() {
        // Simulate FCM calling onFailure with UNREGISTERED
        FirebaseMessagingException fme = mockFcmException(MessagingErrorCode.UNREGISTERED);

        service.evictStaleToken(recipient, fme);

        verify(userRepository).save(recipient);
        assert recipient.getDeviceToken() == null;
    }

    @Test
    @DisplayName("INVALID_ARGUMENT error evicts the device token")
    void invalidArgumentErrorEvictsToken() {
        FirebaseMessagingException fme = mockFcmException(MessagingErrorCode.INVALID_ARGUMENT);

        service.evictStaleToken(recipient, fme);

        verify(userRepository).save(recipient);
        assert recipient.getDeviceToken() == null;
    }

    @Test
    @DisplayName("non-evictable error does NOT evict the token")
    void nonEvictableErrorKeepsToken() {
        FirebaseMessagingException fme = mockFcmException(MessagingErrorCode.UNAVAILABLE);

        service.evictStaleToken(recipient, fme);

        verify(userRepository, never()).save(any());
        assert recipient.getDeviceToken() != null;
    }

    @Test
    @DisplayName("non-Firebase exception does NOT evict the token")
    void nonFirebaseExceptionKeepsToken() {
        RuntimeException rte = new RuntimeException("network timeout");

        service.evictStaleToken(recipient, rte);

        verify(userRepository, never()).save(any());
        assert recipient.getDeviceToken() != null;
    }

    @Test
    @DisplayName("FCM onFailure callback triggers eviction flow")
    void onFailureCallbackTriggersEviction() {
        FirebaseMessagingException fme = mockFcmException(MessagingErrorCode.UNREGISTERED);

        // Wire FcmSender to immediately invoke onFailure
        doAnswer(invocation -> {
            Consumer<Throwable> onFailure = invocation.getArgument(2);
            onFailure.accept(fme);
            return null;
        }).when(fcmSender).sendAsync(any(Message.class), any(Runnable.class), any());

        service.sendToUser(recipient, "Title", "Body", Map.of("type", "chat"));

        verify(userRepository).save(recipient);
        assert recipient.getDeviceToken() == null;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Creates a FirebaseMessagingException with the given error code via
     * reflection, since the constructors and factory methods are not public.
     */
    private static FirebaseMessagingException mockFcmException(MessagingErrorCode code) {
        try {
            // Use the private 5-arg constructor:
            // FirebaseMessagingException(ErrorCode, String, Throwable, IncomingHttpResponse, MessagingErrorCode)
            var constructor = FirebaseMessagingException.class.getDeclaredConstructor(
                    com.google.firebase.ErrorCode.class,
                    String.class,
                    Throwable.class,
                    com.google.firebase.IncomingHttpResponse.class,
                    MessagingErrorCode.class
            );
            constructor.setAccessible(true);
            com.google.firebase.ErrorCode errorCode = switch (code) {
                case UNREGISTERED -> com.google.firebase.ErrorCode.NOT_FOUND;
                case INVALID_ARGUMENT -> com.google.firebase.ErrorCode.INVALID_ARGUMENT;
                case UNAVAILABLE -> com.google.firebase.ErrorCode.UNAVAILABLE;
                default -> com.google.firebase.ErrorCode.INTERNAL;
            };
            return constructor.newInstance(errorCode, "mock error", null, null, code);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock FirebaseMessagingException", e);
        }
    }
}
