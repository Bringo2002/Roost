package com.roost.service;

import com.roost.dto.UserProfileResponse;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profile response tells the app whether the account was created through
 * Google or Apple sign-in. The delete-account screen uses it to decide
 * whether to ask for a password, which social-login accounts do not have.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceProfileTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private GoogleAuthService googleAuthService;

    @InjectMocks
    private AuthService authService;

    private User account(boolean emailVerified) {
        User user = new User();
        user.setId(1L);
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setRole(Role.TENANT);
        user.setEmailVerified(emailVerified);
        return user;
    }

    @Test
    @DisplayName("getProfile reports a social-login account as email-verified")
    void profileReportsSocialLoginAccountAsEmailVerified() {
        UserProfileResponse profile = authService.getProfile(account(true));

        assertTrue(profile.isEmailVerified());
    }

    @Test
    @DisplayName("getProfile reports a password account as not email-verified")
    void profileReportsPasswordAccountAsNotEmailVerified() {
        UserProfileResponse profile = authService.getProfile(account(false));

        assertFalse(profile.isEmailVerified());
    }
}
