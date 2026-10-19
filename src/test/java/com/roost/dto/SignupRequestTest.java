package com.roost.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SignupRequest} Bean Validation constraints.
 * Written RED-first.
 */
class SignupRequestTest {

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("fully valid signup passes")
    void validSignupPasses() {
        SignupRequest r = SignupRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .phone("+254712345678")
                .password("s3cretPass!")
                .build();
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    @DisplayName("null name is invalid")
    void nullNameInvalid() {
        SignupRequest r = SignupRequest.builder()
                .name(null)
                .email("alice@example.com")
                .phone("+254712345678")
                .password("s3cretPass!")
                .build();
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("blank name is invalid")
    void blankNameInvalid() {
        SignupRequest r = SignupRequest.builder()
                .name("   ")
                .email("alice@example.com")
                .phone("+254712345678")
                .password("s3cretPass!")
                .build();
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("null email is invalid")
    void nullEmailInvalid() {
        SignupRequest r = SignupRequest.builder()
                .name("Alice")
                .email(null)
                .phone("+254712345678")
                .password("s3cretPass!")
                .build();
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("blank email is invalid")
    void blankEmailInvalid() {
        SignupRequest r = SignupRequest.builder()
                .name("Alice")
                .email("  ")
                .phone("+254712345678")
                .password("s3cretPass!")
                .build();
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("null password is invalid")
    void nullPasswordInvalid() {
        SignupRequest r = SignupRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .phone("+254712345678")
                .password(null)
                .build();
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("password shorter than 8 chars is invalid")
    void shortPasswordInvalid() {
        SignupRequest r = SignupRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .phone("+254712345678")
                .password("abc")
                .build();
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    @DisplayName("name over 100 chars is invalid")
    void overlongNameInvalid() {
        SignupRequest r = SignupRequest.builder()
                .name("A".repeat(101))
                .email("alice@example.com")
                .phone("+254712345678")
                .password("s3cretPass!")
                .build();
        assertEquals(1, validator.validate(r).size());
    }
}
