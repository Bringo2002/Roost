package com.roost.service;

import com.roost.dto.AppleAuthRequest;
import com.roost.dto.GoogleAuthRequest;
import com.roost.dto.SignupRequest;
import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Security tests for the email pre-hijacking fix (M1).
 *
 * <p>An attacker who pre-registers {@code victim@gmail.com} with a password
 * must NOT receive a valid token when the real victim later signs in via
 * Google or Apple (which cryptographically prove email ownership).
 *
 * <p>Written RED-first: these tests fail until {@code emailVerified} checks
 * are added to {@link AuthService} and {@link AppleLoginService}.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceEmailVerifiedTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private GoogleAuthService googleAuthService;

    @InjectMocks
    private AuthService authService;

    private User unverifiedPasswordAccount;
    private User verifiedSocialAccount;

    @BeforeEach
    void setUp() {
        // Simulates an attacker's pre-registered password account.
        unverifiedPasswordAccount = new User();
        unverifiedPasswordAccount.setId(1L);
        unverifiedPasswordAccount.setEmail("victim@gmail.com");
        unverifiedPasswordAccount.setEmailVerified(false);
        unverifiedPasswordAccount.setRole(Role.TENANT);

        // Simulates a legitimate existing social login account.
        verifiedSocialAccount = new User();
        verifiedSocialAccount.setId(2L);
        verifiedSocialAccount.setEmail("victim@gmail.com");
        verifiedSocialAccount.setEmailVerified(true);
        verifiedSocialAccount.setRole(Role.TENANT);
    }

    // ── Google: pre-hijacking block ───────────────────────────────────────────

    @Test
    @DisplayName("loginWithGoogle blocks merge into an unverified password account (pre-hijacking)")
    void loginWithGoogle_blocksLinkageToUnverifiedAccount() {
        GoogleAuthService.GoogleIdentity identity =
                new GoogleAuthService.GoogleIdentity("victim@gmail.com", "Victim");
        when(googleAuthService.verifyGoogleToken(anyString())).thenReturn(identity);
        when(userRepository.findByEmail("victim@gmail.com"))
                .thenReturn(Optional.of(unverifiedPasswordAccount));

        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("google-token");

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.loginWithGoogle(req));

        // Should be a 409 CONFLICT, not a 200.
        assertEquals(409, ex.getStatus().value(),
                "Expected 409 CONFLICT when merging into unverified account");
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("loginWithGoogle allows login into a provider-verified account")
    void loginWithGoogle_allowsLinkageToVerifiedAccount() {
        GoogleAuthService.GoogleIdentity identity =
                new GoogleAuthService.GoogleIdentity("victim@gmail.com", "Victim");
        when(googleAuthService.verifyGoogleToken(anyString())).thenReturn(identity);
        when(userRepository.findByEmail("victim@gmail.com"))
                .thenReturn(Optional.of(verifiedSocialAccount));
        when(jwtService.generateToken(any())).thenReturn("jwt-token");

        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("google-token");

        assertDoesNotThrow(() -> authService.loginWithGoogle(req));
        verify(jwtService).generateToken(verifiedSocialAccount);
    }

    @Test
    @DisplayName("loginWithGoogle sets emailVerified=true on newly created accounts")
    void loginWithGoogle_setsEmailVerifiedOnNewAccount() {
        GoogleAuthService.GoogleIdentity identity =
                new GoogleAuthService.GoogleIdentity("new@gmail.com", "New User");
        when(googleAuthService.verifyGoogleToken(anyString())).thenReturn(identity);
        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("jwt");

        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("google-token");
        authService.loginWithGoogle(req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertTrue(saved.getValue().isEmailVerified(),
                "New Google accounts must have emailVerified=true");
    }

    // ── Signup: emailVerified must be false ───────────────────────────────────

    @Test
    @DisplayName("signup sets emailVerified=false so it cannot be used for social login linking")
    void signup_setsEmailVerifiedFalse() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("jwt");

        SignupRequest req = SignupRequest.builder()
                .name("Alice").email("alice@example.com")
                .phone("+254700000000").password("pass1234")
                .build();
        authService.signup(req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertFalse(saved.getValue().isEmailVerified(),
                "Password-signup accounts must start with emailVerified=false");
    }
}
