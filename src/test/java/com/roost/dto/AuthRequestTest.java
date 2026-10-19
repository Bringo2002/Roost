package com.roost.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AuthRequest} Bean Validation constraints.
 * Written RED-first: these tests fail until @NotBlank/@Email constraints
 * are added to AuthRequest and @Valid is wired in AuthController.
 */
class AuthRequestTest {

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("valid email and password passes validation")
    void validRequestPasses() {
        AuthRequest r = new AuthRequest("user@example.com", "s3cret!");
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    @DisplayName("null email is invalid")
    void nullEmailInvalid() {
        AuthRequest r = new AuthRequest(null, "s3cret!");
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("blank email is invalid")
    void blankEmailInvalid() {
        AuthRequest r = new AuthRequest("   ", "s3cret!");
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("null password is invalid")
    void nullPasswordInvalid() {
        AuthRequest r = new AuthRequest("user@example.com", null);
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("blank password is invalid")
    void blankPasswordInvalid() {
        AuthRequest r = new AuthRequest("user@example.com", "  ");
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("both fields null produces two violations")
    void bothNullProducesTwoViolations() {
        AuthRequest r = new AuthRequest();
        assertEquals(2, validator.validate(r).size());
    }
}
