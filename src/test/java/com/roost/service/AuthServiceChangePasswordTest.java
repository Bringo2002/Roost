package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Changing a password must enforce the same minimum length as signup, so a
 * user can't sign up with 8 characters and then weaken the account by
 * changing to a shorter one.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceChangePasswordTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private GoogleAuthService googleAuthService;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("ada@example.com");
        user.setPassword("stored-hash");
        user.setRole(Role.TENANT);
    }

    @Test
    @DisplayName("rejects a 7-character new password, one short of the signup minimum")
    void rejectsPasswordShorterThanSignupMinimum() {
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.changePassword(user, "current-pass", "1234567"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("at least 8"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("accepts an 8-character new password and stores its hash")
    void acceptsPasswordAtTheMinimum() {
        when(passwordEncoder.matches("current-pass", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("12345678")).thenReturn("new-hash");

        authService.changePassword(user, "current-pass", "12345678");

        assertEquals("new-hash", user.getPassword());
        verify(userRepository).save(user);
    }
}
