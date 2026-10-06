package com.roost.service;

import com.roost.dto.AppleAuthRequest;
import com.roost.dto.AuthResponse;
import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Sign in with Apple: verifies the token via {@link AppleAuthService}, then
 * logs the person into the account with that email, creating a plain
 * TENANT account if none exists. Same find-or-create contract as
 * {@code AuthService.loginWithGoogle}, kept in its own class so the
 * existing Google/email flows are untouched.
 */
@Service
@Transactional
public class AppleLoginService {

    private static final String DEFAULT_NAME = "Roost User";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppleAuthService appleAuthService;

    public AppleLoginService(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             JwtService jwtService,
                             AppleAuthService appleAuthService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appleAuthService = appleAuthService;
    }

    /**
     * Handles both Apple sign-up and sign-in. A new account gets a bcrypt
     * hash of a random UUID as its password (the column is NOT NULL; the
     * user never knows or needs it). An existing email is logged in as-is,
     * since Apple has verified that email belongs to the caller.
     *
     * @throws ApiException 400 if the token cannot be verified
     */
    public AuthResponse loginWithApple(AppleAuthRequest request) {
        AppleAuthService.AppleIdentity identity = appleAuthService.verifyAppleToken(request.getIdToken());
        if (identity == null) {
            throw ApiException.badRequest("Apple sign-in could not be verified. Please try again.");
        }

        return userRepository.findByEmail(identity.email())
                .map(existing -> AuthResponse.builder()
                        .token(jwtService.generateToken(existing))
                        .isNewUser(false)
                        .build())
                .orElseGet(() -> {
                    User user = new User();
                    user.setName(resolveName(identity, request));
                    user.setEmail(identity.email());
                    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    user.setRole(Role.TENANT);
                    userRepository.save(user);
                    return AuthResponse.builder()
                            .token(jwtService.generateToken(user))
                            .isNewUser(true)
                            .build();
                });
    }

    /** Token name first, then the client-forwarded name, then a neutral default. */
    private static String resolveName(AppleAuthService.AppleIdentity identity, AppleAuthRequest request) {
        if (identity.name() != null) return identity.name();
        String fromClient = request.getName();
        if (fromClient != null && !fromClient.isBlank()) return fromClient.trim();
        return DEFAULT_NAME;
    }
}
