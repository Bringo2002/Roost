package com.roost.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** HS256 needs a key of at least 256 bits. */
    private static final int MIN_KEY_BYTES = 32;

    // No hardcoded fallback here on purpose -- a default checked into a
    // public repo stops being a secret. jwt.secret must come from
    // application.properties (local dev only) or the JWT_SECRET env var
    // (production); if neither is set, Spring fails to start rather than
    // silently signing tokens with a well-known key.
    @Value("${jwt.secret}")
    private String secretKey;

    private volatile byte[] keyBytes;

    /**
     * Decode and validate the secret once at startup so a malformed
     * JWT_SECRET fails the deploy instead of failing every login at
     * runtime (a stray trailing newline in the env var previously
     * surfaced as "Input byte array has incorrect ending byte").
     */
    @PostConstruct
    void initKey() {
        keyBytes = decodeSecret(secretKey);
    }

    /**
     * Strips surrounding whitespace (the usual copy/paste damage), decodes
     * base64, and enforces the minimum key length. Error messages report
     * lengths only -- never the secret itself.
     */
    static byte[] decodeSecret(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("jwt.secret is not set");
        }
        String cleaned = raw.strip();
        if (cleaned.length() != raw.length()) {
            log.warn("jwt.secret contained {} surrounding whitespace character(s); trimmed. "
                    + "Re-save the JWT_SECRET variable without them.", raw.length() - cleaned.length());
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "jwt.secret is not valid base64 (length=" + cleaned.length() + ")", e);
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must decode to at least " + MIN_KEY_BYTES + " bytes (got " + bytes.length + ")");
        }
        return bytes;
    }

    public String extractUsername(String token) {
        try {
            return parseAndVerify(token).getJWTClaimsSet().getSubject();
        } catch (ParseException | JOSEException e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(Map.of(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        try {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(userDetails.getUsername())
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30)); // 30 days

            extraClaims.forEach(claimsBuilder::claim);

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsBuilder.build()
            );
            JWSSigner signer = new MACSigner(getSecretKeyBytes());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            SignedJWT signedJWT = parseAndVerify(token);
            String username = signedJWT.getJWTClaimsSet().getSubject();
            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
            return username.equals(userDetails.getUsername())
                    && expiration != null
                    && expiration.after(new Date());
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }

    private SignedJWT parseAndVerify(String token) throws ParseException, JOSEException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        MACVerifier verifier = new MACVerifier(getSecretKeyBytes());
        if (!signedJWT.verify(verifier)) {
            throw new JOSEException("JWT signature verification failed");
        }
        return signedJWT;
    }

    private byte[] getSecretKeyBytes() {
        // Normally set by initKey() at startup. Decode on first use when a
        // JwtService is built by hand (tests) and initKey() was never called.
        byte[] bytes = keyBytes;
        if (bytes == null) {
            bytes = decodeSecret(secretKey);
            keyBytes = bytes;
        }
        return bytes;
    }
}
