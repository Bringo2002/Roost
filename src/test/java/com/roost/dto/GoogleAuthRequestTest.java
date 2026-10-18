package com.roost.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GoogleAuthRequest} Bean Validation constraints.
 * Written RED-first.
 */
class GoogleAuthRequestTest {

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("valid idToken passes validation")
    void validTokenPasses() {
        GoogleAuthRequest r = new GoogleAuthRequest();
        r.setIdToken("ya29.valid-google-token");
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    @DisplayName("null idToken is invalid")
    void nullTokenInvalid() {
        GoogleAuthRequest r = new GoogleAuthRequest();
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("blank idToken is invalid")
    void blankTokenInvalid() {
        GoogleAuthRequest r = new GoogleAuthRequest();
        r.setIdToken("   ");
        assertEquals(1, validator.validate(r).size());
    }
}
