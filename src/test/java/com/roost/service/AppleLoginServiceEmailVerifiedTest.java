package com.roost.service;

import com.roost.dto.AppleAuthRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Security tests for the email pre-hijacking fix (M1) in {@link AppleLoginService}.
 * Written RED-first — fails until {@code isEmailVerified()} check is added.
 */
@ExtendWith(MockitoExtension.class)
class AppleLoginServiceEmailVerifiedTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AppleAuthService appleAuthService;

    @InjectMocks
    private AppleLoginService appleLoginService;

    private AppleAuthService.AppleIdentity identity;
    private User unverifiedPasswordAccount;
    private User verifiedSocialAccount;

    @BeforeEach
    void setUp() {
        identity = new AppleAuthService.AppleIdentity("victim@icloud.com", "Victim");

        unverifiedPasswordAccount = new User();
        unverifiedPasswordAccount.setId(1L);
        unverifiedPasswordAccount.setEmail("victim@icloud.com");
        unverifiedPasswordAccount.setEmailVerified(false);
        unverifiedPasswordAccount.setRole(Role.TENANT);

        verifiedSocialAccount = new User();
        verifiedSocialAccount.setId(2L);
        verifiedSocialAccount.setEmail("victim@icloud.com");
        verifiedSocialAccount.setEmailVerified(true);
        verifiedSocialAccount.setRole(Role.TENANT);
    }

    @Test
    @DisplayName("loginWithApple blocks merge into an unverified password account (pre-hijacking)")
    void loginWithApple_blocksLinkageToUnverifiedAccount() {
        when(appleAuthService.verifyAppleToken(anyString())).thenReturn(identity);
        when(userRepository.findByEmail("victim@icloud.com"))
                .thenReturn(Optional.of(unverifiedPasswordAccount));

        AppleAuthRequest req = new AppleAuthRequest();
        req.setIdToken("apple-token");

        ApiException ex = assertThrows(ApiException.class,
                () -> appleLoginService.loginWithApple(req));

        assertEquals(409, ex.getStatus().value(),
                "Expected 409 CONFLICT when merging into unverified account");
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("loginWithApple allows login into a provider-verified account")
    void loginWithApple_allowsLinkageToVerifiedAccount() {
        when(appleAuthService.verifyAppleToken(anyString())).thenReturn(identity);
        when(userRepository.findByEmail("victim@icloud.com"))
                .thenReturn(Optional.of(verifiedSocialAccount));
        when(jwtService.generateToken(any())).thenReturn("jwt");

        AppleAuthRequest req = new AppleAuthRequest();
        req.setIdToken("apple-token");

        assertDoesNotThrow(() -> appleLoginService.loginWithApple(req));
        verify(jwtService).generateToken(verifiedSocialAccount);
    }

    @Test
    @DisplayName("loginWithApple sets emailVerified=true on newly created accounts")
    void loginWithApple_setsEmailVerifiedOnNewAccount() {
        when(appleAuthService.verifyAppleToken(anyString())).thenReturn(identity);
        when(userRepository.findByEmail("victim@icloud.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("jwt");

        AppleAuthRequest req = new AppleAuthRequest();
        req.setIdToken("apple-token");
        appleLoginService.loginWithApple(req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertTrue(saved.getValue().isEmailVerified(),
                "New Apple accounts must have emailVerified=true");
    }
}
