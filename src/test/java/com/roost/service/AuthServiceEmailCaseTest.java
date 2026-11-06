package com.roost.service;

import com.roost.dto.GoogleAuthRequest;
import com.roost.dto.SignupRequest;
import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Emails differing only by case or padding are the same account: signup,
 * login and Google sign-in must all look up and store the canonical form.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceEmailCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private GoogleAuthService googleAuthService;

    @InjectMocks
    private AuthService authService;

    private static SignupRequest signupFor(String email) {
        return SignupRequest.builder()
                .name("Ada").email(email)
                .phone("+254700000000").password("pass1234")
                .build();
    }

    @Test
    @DisplayName("signup stores the email trimmed and lowercased")
    void signup_storesCanonicalEmail() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("jwt");

        authService.signup(signupFor("  Ada@Example.COM "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("ada@example.com", saved.getValue().getEmail());
    }

    @Test
    @DisplayName("signup rejects an email that differs from an existing account only by case")
    void signup_rejectsCaseVariantOfExistingEmail() {
        User existing = new User();
        existing.setEmail("ada@example.com");
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(existing));

        assertThrows(ApiException.class,
                () -> authService.signup(signupFor("Ada@Example.com")));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login authenticates and looks up the canonical email, whatever the typed case")
    void login_usesCanonicalEmail() {
        User existing = new User();
        existing.setEmail("ada@example.com");
        existing.setRole(Role.TENANT);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(existing)).thenReturn("jwt");

        authService.login("  Ada@Example.COM", "pass1234");

        ArgumentCaptor<Authentication> auth = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(auth.capture());
        assertEquals("ada@example.com", auth.getValue().getPrincipal());
    }

    @Test
    @DisplayName("Google sign-in creates new accounts with the canonical email")
    void loginWithGoogle_storesCanonicalEmail() {
        GoogleAuthService.GoogleIdentity identity =
                new GoogleAuthService.GoogleIdentity("New.User@Gmail.COM", "New User");
        when(googleAuthService.verifyGoogleToken(anyString())).thenReturn(identity);
        when(userRepository.findByEmail("new.user@gmail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("jwt");

        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setIdToken("google-token");
        authService.loginWithGoogle(req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("new.user@gmail.com", saved.getValue().getEmail());
    }
}
