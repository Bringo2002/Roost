package com.roost.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    // No hardcoded fallback here on purpose -- a default checked into a
    // public repo stops being a secret. jwt.secret must come from
    // application.properties (local dev only) or the JWT_SECRET env var
    // (production); if neither is set, Spring fails to start rather than
    // silently signing tokens with a well-known key.
    @Value("${jwt.secret}")
    private String secretKey;

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
        return Base64.getDecoder().decode(secretKey);
    }
}
