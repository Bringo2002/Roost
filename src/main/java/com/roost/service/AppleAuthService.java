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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verifies Sign in with Apple server-side. The Flutter client authenticates
 * with Firebase Auth's Apple provider and sends us the resulting Firebase
 * ID token; we verify it here and trust the email it carries. Mirrors
 * {@link GoogleAuthService} and shares the same FIREBASE_SERVICE_ACCOUNT_JSON
 * credential and FirebaseApp instance (same isEmpty() guard pattern).
 */
@Service
public class AppleAuthService {

    private static final Logger log = Logger.getLogger(AppleAuthService.class.getName());
    private static final String APPLE_PROVIDER = "apple.com";
    private final boolean configured;

    public AppleAuthService(@Value("${firebase.service-account-json:}") String serviceAccountJson) {
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
                log.log(Level.WARNING, "Apple sign-in disabled -- invalid FIREBASE_SERVICE_ACCOUNT_JSON", e);
            }
        }
        this.configured = ok;
    }

    /** @return true when Firebase credentials loaded and tokens can be verified. */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Verifies {@code idToken} and returns the Apple identity it attests to,
     * or null if verification fails for any reason (expired, wrong project,
     * tampered, not an Apple sign-in, unconfigured). Never throws -- callers
     * treat null as "verification failed", not a server error.
     */
    public AppleIdentity verifyAppleToken(String idToken) {
        if (!configured || idToken == null || idToken.isBlank()) return null;
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            return toIdentity(decoded.getClaims(), decoded.getEmail(), decoded.isEmailVerified(), decoded.getName());
        } catch (Exception e) {
            log.info("Apple token verification failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Applies the trust rules to already-decoded token data. Split out from
     * {@link #verifyAppleToken} so the rules are testable without a live
     * Firebase project.
     *
     * The firebase.sign_in_provider claim must be "apple.com" so a token
     * from Google, email/password or phone auth can't be replayed against
     * this endpoint to fake an Apple identity. The email must be present
     * and verified. Note it may be an Apple private-relay address.
     *
     * @return the identity, or null if any rule fails
     */
    static AppleIdentity toIdentity(Map<String, Object> claims, String email, boolean emailVerified, String name) {
        Object firebaseClaims = claims == null ? null : claims.get("firebase");
        if (!(firebaseClaims instanceof Map<?, ?> firebaseMap)
                || !APPLE_PROVIDER.equals(firebaseMap.get("sign_in_provider"))) {
            return null;
        }
        if (email == null || email.isBlank() || !emailVerified) return null;

        String cleanName = (name != null && !name.isBlank()) ? name.trim() : null;
        return new AppleIdentity(email, cleanName);
    }

    /** Verified Apple identity. {@code name} is null when the token carries none. */
    public record AppleIdentity(String email, String name) {}
}
