package com.roost.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailNormalizerTest {

    @Test
    @DisplayName("lowercases the whole address")
    void lowercases() {
        assertEquals("ada@example.com", EmailNormalizer.normalize("Ada@Example.COM"));
    }

    @Test
    @DisplayName("trims surrounding whitespace")
    void trims() {
        assertEquals("ada@example.com", EmailNormalizer.normalize("  ada@example.com \t"));
    }

    @Test
    @DisplayName("leaves an already-canonical address unchanged, including plus-tags")
    void keepsCanonicalAddress() {
        assertEquals("ada+tag@example.co.ke", EmailNormalizer.normalize("ada+tag@example.co.ke"));
    }

    @Test
    @DisplayName("passes null through so callers keep their own null handling")
    void nullStaysNull() {
        assertNull(EmailNormalizer.normalize(null));
    }
}
