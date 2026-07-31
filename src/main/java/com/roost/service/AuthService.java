package com.roost.service;

import com.roost.dto.AuthResponse;
import com.roost.dto.GoogleAuthRequest;
import com.roost.dto.SignupRequest;
import com.roost.dto.UserProfileResponse;
import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final GoogleAuthService googleAuthService;

    public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuthenticationManager authenticationManager,
                        GoogleAuthService googleAuthService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.googleAuthService = googleAuthService;
    }

    public AuthResponse signup(SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw ApiException.badRequest("Email is already in use.");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.TENANT);
        userRepository.save(user);

        return AuthResponse.builder().token(jwtService.generateToken(user)).build();
    }

    public AuthResponse login(String email, String password) {
        // Throws AuthenticationException (a RuntimeException) on bad
        // credentials, which GlobalExceptionHandler already maps to a
        // 400 with the underlying message.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.badRequest("User not found"));
        return AuthResponse.builder().token(jwtService.generateToken(user)).build();
    }

    /**
     * Handles both Google sign-up and Google sign-in through one endpoint,
     * matched by email (Google/Firebase already verified that email
     * belongs to whoever is signing in, so it's safe to treat "email
     * already registered" as "log this person into that account" rather
     * than rejecting it -- same account, another way in).
     *
     * A brand-new account gets password set to a bcrypt hash of a random
     * UUID: there's no password this user knows or will ever be asked
     * for, it just satisfies the column's NOT NULL constraint and can
     * never coincidentally match anything a real login attempt would send.
     */
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        GoogleAuthService.GoogleIdentity identity = googleAuthService.verifyGoogleToken(request.getIdToken());
        if (identity == null) {
            throw ApiException.badRequest("Google sign-in could not be verified. Please try again.");
        }

        return userRepository.findByEmail(identity.email())
                .map(existing -> AuthResponse.builder()
                        .token(jwtService.generateToken(existing))
                        .isNewUser(false)
                        .build())
                .orElseGet(() -> {
                    User user = new User();
                    user.setName(identity.name());
                    user.setEmail(identity.email());
                    user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
                    user.setRole(Role.TENANT);
                    userRepository.save(user);
                    return AuthResponse.builder()
                            .token(jwtService.generateToken(user))
                            .isNewUser(true)
                            .build();
                });
    }

    public UserProfileResponse getProfile(User user) {
        return toProfileResponse(user);
    }

    public UserProfileResponse updateProfile(User user, Map<String, String> updates) {
        if (updates.containsKey("name")) user.setName(updates.get("name"));
        if (updates.containsKey("phone")) user.setPhone(updates.get("phone"));
        userRepository.save(user);
        return toProfileResponse(user);
    }

    public void verifyPhone(User user) {
        user.setPhoneVerified(true);
        userRepository.save(user);
    }

    /**
     * Upgrades a plain (TENANT) account to LANDLORD. This is the only
     * path that ever sets a user's role after signup -- role is never
     * chosen at registration, only granted here when someone explicitly
     * asks to list a property. Idempotent: calling it again on an
     * existing landlord is a no-op rather than an error, since the
     * client can't easily know in advance whether it already ran.
     */
    public UserProfileResponse becomeLandlord(User user) {
        if (user.getRole() != Role.LANDLORD) {
            user.setRole(Role.LANDLORD);
            userRepository.save(user);
        }
        return toProfileResponse(user);
    }

    /**
     * Reverts a LANDLORD back to a plain browsing (TENANT) account.
     * Existing listings are left as-is -- this only affects what the
     * account can do going forward, not past data. Refuses to touch
     * ADMIN accounts; that role isn't something this self-service
     * endpoint should ever be able to change.
     */
    public UserProfileResponse revertToTenant(User user) {
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("Admin accounts cannot be changed through this endpoint.");
        }
        if (user.getRole() != Role.TENANT) {
            user.setRole(Role.TENANT);
            userRepository.save(user);
        }
        return toProfileResponse(user);
    }

    public void changePassword(User user, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw ApiException.badRequest("Current password is required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw ApiException.badRequest("New password is required");
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("New password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw ApiException.badRequest("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .phoneVerified(user.isPhoneVerified())
                .role(user.getRole())
                .publicKey(user.getPublicKey())
                .lastActiveAt(user.getLastActiveAt())
                .build();
    }
}
