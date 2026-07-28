package com.roost.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verifies Google Sign-In server-side. The Flutter client authenticates
 * with Firebase Auth's Google provider and sends us the resulting
 * Firebase ID token; we verify it here and trust the email/name it
 * carries. Shares the same FIREBASE_SERVICE_ACCOUNT_JSON credential and
 * FirebaseApp instance as FirebasePushService/FirebasePhoneAuthService --
 * whichever of the three Spring constructs first performs the actual
 * app init, the others just reuse it (same isEmpty() guard pattern).
 */
@Service
public class GoogleAuthService {

    private static final Logger log = Logger.getLogger(GoogleAuthService.class.getName());
    private final boolean configured;

    public GoogleAuthService(@Value("${firebase.service-account-json:}") String serviceAccountJson) {
        boolean ok = false;
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            try {
                if (FirebaseApp.getApps().isEmpty()) {
                    GoogleCredentials credentials = GoogleCredentials.fromStream(
                            new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
                    FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build());
                }
                ok = true;
            } catch (IOException e) {
                log.log(Level.WARNING, "Google sign-in disabled -- invalid FIREBASE_SERVICE_ACCOUNT_JSON", e);
            }
        }
        this.configured = ok;
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Verifies [idToken] and returns the Google identity it attests to,
     * or null if verification fails for any reason (expired, wrong
     * project, tampered, not actually a Google sign-in, unconfigured).
     * Never throws -- callers treat null as "verification failed," not a
     * server error.
     *
     * We additionally require the token's firebase.sign_in_provider claim
     * to be "google.com" so a token from email/password or phone auth
     * can't be replayed against this endpoint to fake a Google identity.
     */
    public GoogleIdentity verifyGoogleToken(String idToken) {
        if (!configured) return null;
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);

            Object firebaseClaims = decoded.getClaims().get("firebase");
            if (!(firebaseClaims instanceof java.util.Map<?, ?> firebaseMap)
                    || !"google.com".equals(firebaseMap.get("sign_in_provider"))) {
                return null;
            }

            String email = decoded.getEmail();
            if (email == null || !decoded.isEmailVerified()) return null;

            String name = decoded.getName();
            return new GoogleIdentity(email, (name != null && !name.isBlank()) ? name : email);
        } catch (Exception e) {
            log.info("Google token verification failed: " + e.getMessage());
            return null;
        }
    }

    public record GoogleIdentity(String email, String name) {}
}
