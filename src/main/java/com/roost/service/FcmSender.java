package com.roost.service;

import com.google.firebase.messaging.Message;

/**
 * Thin abstraction over Firebase Cloud Messaging's send call. Exists solely
 * so {@link FirebasePushService} can be unit-tested without a live Firebase
 * project — the production implementation delegates to
 * {@link com.google.firebase.messaging.FirebaseMessaging#sendAsync}, while
 * tests inject a stub.
 */
public interface FcmSender {

    /**
     * Sends a push message asynchronously. Implementations must be
     * fire-and-forget: callers do not wait for the result.
     *
     * @param message      the fully-built FCM message
     * @param onSuccess    callback invoked when FCM accepts the message
     * @param onFailure    callback invoked when FCM rejects the message
     */
    void sendAsync(Message message, Runnable onSuccess, java.util.function.Consumer<Throwable> onFailure);
}
