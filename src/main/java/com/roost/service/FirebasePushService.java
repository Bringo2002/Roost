package com.roost.service;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends push notifications to users' registered devices via Firebase
 * Cloud Messaging. Configured via the FIREBASE_SERVICE_ACCOUNT_JSON env
 * var (the full contents of a Firebase service account key file, from
 * Firebase Console -> Project Settings -> Service Accounts). If unset,
 * the service stays inert (like R2StorageService before it) rather than
 * failing app startup or breaking the action that triggered a push --
 * sending a chat message must always succeed even if the resulting
 * notification can't be delivered.
 *
 * For end-to-end encrypted chat specifically: the server never has the
 * keys to read message content, so pushes sent from here must stay
 * generic ("New message from X") -- never plaintext message content.
 */
@Service
@DependsOn("firebaseConfig")
public class FirebasePushService {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushService.class);

    /** FCM error codes that mean the token is permanently invalid. */
    private static final Set<MessagingErrorCode> EVICTABLE_ERRORS = Set.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT
    );

    /** Max pushes per user per minute before throttling kicks in. */
    private static final int MAX_PUSHES_PER_MINUTE = 10;

    /** Per-user send counters, reset every 60s by {@link #resetPushCounters()}. */
    private final ConcurrentHashMap<Long, AtomicInteger> pushCounters = new ConcurrentHashMap<>();

    private final boolean configured;
    private final UserRepository userRepository;

    public FirebasePushService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.configured = !FirebaseApp.getApps().isEmpty();
        if (!configured) {
            log.warn("Firebase push disabled: FirebaseApp not initialized");
        }
    }

    /** True once a valid service account credential has been loaded. */
    public boolean isConfigured() {
        return configured;
    }

    /** Sends a plain title/body push with no extra data payload. */
    public void sendToUser(User recipient, String title, String body) {
        sendToUser(recipient, title, body, Map.of());
    }

    /**
     * Sends a push notification to [recipient]'s registered device, if
     * any. Fire-and-forget: uses sendAsync() rather than send() so the
     * caller (e.g. the chat message HTTP handler) never blocks on a
     * round trip to FCM's servers. Never throws -- a delivery failure
     * (stale token, network issue, unconfigured service) is logged and
     * swallowed so it can never break the calling code path.
     */
    public void sendToUser(User recipient, String title, String body, Map<String, String> data) {
        if (!configured || recipient == null) return;
        String token = recipient.getDeviceToken();
        if (token == null || token.isBlank()) return;

        // Per-user rate limit: drop excess pushes rather than flooding a device.
        AtomicInteger counter = pushCounters.computeIfAbsent(recipient.getId(), k -> new AtomicInteger(0));
        if (counter.incrementAndGet() > MAX_PUSHES_PER_MINUTE) {
            log.warn("push.throttled userId={} count={}", recipient.getId(), counter.get());
            return;
        }

        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build();

        ApiFuture<String> future = FirebaseMessaging.getInstance().sendAsync(message);
        ApiFutures.addCallback(future, new ApiFutureCallback<String>() {
            @Override
            public void onSuccess(String result) {
                log.info("push.sent userId={} type={}", recipient.getId(),
                        data.getOrDefault("type", "unknown"));
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("push.failed userId={} type={} error={}", recipient.getId(),
                        data.getOrDefault("type", "unknown"), t.getMessage());
                evictStaleToken(recipient, t);
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * If FCM says the token is permanently invalid (app uninstalled, token
     * rotated), null it out so we stop wasting round-trips on every future
     * push to this user. The user's next app launch will register a fresh token.
     */
    private void evictStaleToken(User recipient, Throwable t) {
        if (!(t instanceof FirebaseMessagingException fme)) return;
        if (!EVICTABLE_ERRORS.contains(fme.getMessagingErrorCode())) return;
        try {
            recipient.setDeviceToken(null);
            userRepository.save(recipient);
            log.info("push.token.evicted userId={} reason={}", recipient.getId(), fme.getMessagingErrorCode());
        } catch (Exception e) {
            log.warn("push.token.eviction.failed userId={}", recipient.getId(), e);
        }
    }

    /** Resets per-user push counters every 60 seconds. */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60_000)
    void resetPushCounters() {
        pushCounters.clear();
    }
}
