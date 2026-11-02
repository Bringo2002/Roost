package com.roost.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The default profile falls back to a dev-only JWT secret when JWT_SECRET is
 * unset. JwtService decodes the secret as base64, so that fallback must itself
 * be valid base64 of at least 256 bits, or local runs cannot sign tokens.
 */
class DevJwtFallbackTest {

    private static final Pattern FALLBACK =
            Pattern.compile("(?m)^jwt\\.secret=\\$\\{JWT_SECRET:([^}]*)}\\s*$");

    private String devFallback() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(in, "application.properties not on the classpath");
            String properties = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher match = FALLBACK.matcher(properties);
            assertTrue(match.find(), "jwt.secret default not found in application.properties");
            return match.group(1);
        }
    }

    @Test
    @DisplayName("the dev fallback is valid base64")
    void devFallbackIsValidBase64() throws Exception {
        String fallback = devFallback();

        Base64.getDecoder().decode(fallback);
        assertEquals(fallback.strip(), fallback, "fallback must not carry stray whitespace");
    }

    @Test
    @DisplayName("the dev fallback is at least 256 bits")
    void devFallbackIsLongEnough() throws Exception {
        assertTrue(Base64.getDecoder().decode(devFallback()).length >= 32);
    }
}
