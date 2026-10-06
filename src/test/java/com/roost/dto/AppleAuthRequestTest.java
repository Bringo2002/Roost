package com.roost.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppleAuthRequestTest {

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("token only (name omitted) is valid")
    void tokenOnlyIsValid() {
        AppleAuthRequest r = new AppleAuthRequest();
        r.setIdToken("t");

        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    @DisplayName("null or blank idToken is invalid")
    void blankTokenInvalid() {
        AppleAuthRequest r = new AppleAuthRequest();
        assertEquals(1, validator.validate(r).size());

        r.setIdToken("   ");
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("name over 100 chars is invalid")
    void overlongNameInvalid() {
        AppleAuthRequest r = new AppleAuthRequest();
        r.setIdToken("t");
        r.setName("x".repeat(101));

        assertEquals(1, validator.validate(r).size());
    }
}
