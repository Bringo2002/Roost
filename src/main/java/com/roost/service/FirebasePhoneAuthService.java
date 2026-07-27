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
 * Verifies Firebase phone-auth ID tokens server-side -- confirms a user
 * actually completed the client-side SMS OTP flow before we trust their
 * phone number. Shares the same FIREBASE_SERVICE_ACCOUNT_JSON credential
 * and FirebaseApp instance as FirebasePushService; whichever of the two
 * services Spring constructs first performs the actual app init, the
 * other just reuses it (same isEmpty() guard pattern both ways).
 */
@Service
public class FirebasePhoneAuthService {

    private static final Logger log = Logger.getLogger(FirebasePhoneAuthService.class.getName());
    private final boolean configured;

    public FirebasePhoneAuthService(@Value("${firebase.service-account-json:}") String serviceAccountJson) {
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
                log.log(Level.WARNING, "Firebase phone auth disabled -- invalid FIREBASE_SERVICE_ACCOUNT_JSON", e);
            }
        }
        this.configured = ok;
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Verifies [idToken] and returns the E.164 phone number it attests
     * to, or null if verification fails for any reason (expired, wrong
     * project, tampered, no phone claim, service unconfigured). Never
     * throws -- callers treat null as "verification failed," not a
     * server error.
     */
    public String verifyPhoneToken(String idToken) {
        if (!configured) return null;
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            Object phone = decoded.getClaims().get("phone_number");
            return phone != null ? phone.toString() : null;
        } catch (Exception e) {
            log.info("Phone token verification failed: " + e.getMessage());
            return null;
        }
    }
}
