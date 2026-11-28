package com.roost.service;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Production {@link FcmSender} that delegates to Firebase Cloud Messaging's
 * async send API. Registered as a Spring bean so it can be auto-wired into
 * {@link FirebasePushService} in production, while tests inject a mock.
 */
@Component
public class FirebaseFcmSender implements FcmSender {

    @Override
    public void sendAsync(Message message, Runnable onSuccess, Consumer<Throwable> onFailure) {
        ApiFuture<String> future = FirebaseMessaging.getInstance().sendAsync(message);
        ApiFutures.addCallback(future, new ApiFutureCallback<String>() {
            @Override
            public void onSuccess(String result) {
                onSuccess.run();
            }

            @Override
            public void onFailure(Throwable t) {
                onFailure.accept(t);
            }
        }, MoreExecutors.directExecutor());
    }
}
