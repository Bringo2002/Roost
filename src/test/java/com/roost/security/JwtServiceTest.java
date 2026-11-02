package com.roost.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final byte[] KEY_BYTES =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8); // 32 bytes
    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(KEY_BYTES); // 44 chars

    @Test
    @DisplayName("decodeSecret returns the key bytes for a clean secret")
    void decodeSecret_cleanSecret_returnsBytes() {
        assertArrayEquals(KEY_BYTES, JwtService.decodeSecret(VALID_SECRET));
    }

    @Test
    @DisplayName("decodeSecret trims a trailing newline (the production incident)")
    void decodeSecret_trailingNewline_isTrimmed() {
        assertArrayEquals(KEY_BYTES, JwtService.decodeSecret(VALID_SECRET + "\n"));
    }

    @Test
    @DisplayName("decodeSecret trims surrounding CRLF and spaces")
    void decodeSecret_surroundingWhitespace_isTrimmed() {
        assertArrayEquals(KEY_BYTES, JwtService.decodeSecret("  " + VALID_SECRET + "\r\n"));
    }

    @Test
    @DisplayName("decodeSecret rejects null and blank values")
    void decodeSecret_nullOrBlank_throws() {
        assertThrows(IllegalStateException.class, () -> JwtService.decodeSecret(null));
        assertThrows(IllegalStateException.class, () -> JwtService.decodeSecret("   \n"));
    }

    @Test
    @DisplayName("decodeSecret rejects a double-pasted secret without leaking it")
    void decodeSecret_doublePasted_throwsWithoutLeakingSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> JwtService.decodeSecret(VALID_SECRET + VALID_SECRET));
        assertTrue(ex.getMessage().contains("not valid base64"));
        assertFalse(ex.getMessage().contains(VALID_SECRET));
    }

    @Test
    @DisplayName("decodeSecret rejects keys shorter than 32 bytes")
    void decodeSecret_shortKey_throws() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> JwtService.decodeSecret(shortSecret));
        assertTrue(ex.getMessage().contains("at least 32 bytes"));
    }

    @Test
    @DisplayName("tokens sign and verify when the configured secret has a trailing newline")
    void generateToken_secretWithTrailingNewline_roundTrips() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", VALID_SECRET + "\n");
        service.initKey();
        UserDetails user = User.withUsername("tenant@example.com").password("x").authorities("USER").build();

        String token = service.generateToken(user);

        assertEquals("tenant@example.com", service.extractUsername(token));
        assertTrue(service.isTokenValid(token, user));
    }

    @Test
    @DisplayName("startup fails fast when the secret is malformed")
    void initKey_malformedSecret_failsFast() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", "not base64 !!");
        assertThrows(IllegalStateException.class, service::initKey);
    }

    @Test
    @DisplayName("a hand-built service decodes the secret on first use when initKey was not called")
    void generateToken_withoutInitKey_decodesLazily() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", VALID_SECRET + "\n");
        UserDetails user = User.withUsername("tenant@example.com").password("x").authorities("USER").build();

        String token = service.generateToken(user);

        assertEquals("tenant@example.com", service.extractUsername(token));
    }

    @Test
    @DisplayName("a hand-built service with a malformed secret fails on first use with a clear message")
    void generateToken_withoutInitKeyAndMalformedSecret_throwsClearly() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", "not base64 !!");
        UserDetails user = User.withUsername("tenant@example.com").password("x").authorities("USER").build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generateToken(user));

        assertTrue(ex.getMessage().contains("not valid base64"));
    }
}
