package com.roost.service;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.roost.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class FirebasePushService {

    private static final Logger log = Logger.getLogger(FirebasePushService.class.getName());

    private final boolean configured;

    public FirebasePushService(@Value("${firebase.service-account-json:}") String serviceAccountJson) {
        boolean ok = false;
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            try {
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                }
                ok = true;
            } catch (IOException e) {
                log.log(Level.WARNING, "Firebase push disabled -- invalid FIREBASE_SERVICE_ACCOUNT_JSON", e);
            }
        }
        this.configured = ok;
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

        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build();

        ApiFuture<String> future = FirebaseMessaging.getInstance().sendAsync(message);
        ApiFutures.addCallback(future, new ApiFutureCallback<String>() {
            @Override
            public void onSuccess(String result) {
                // No-op -- FCM accepted the message for delivery.
            }

            @Override
            public void onFailure(Throwable t) {
                log.log(Level.INFO, "Push delivery failed for user " + recipient.getId() + ": " + t.getMessage());
            }
        }, MoreExecutors.directExecutor());
    }
}
