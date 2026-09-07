package com.roost.controller;

import com.roost.dto.AuthRequest;
import com.roost.dto.AuthResponse;
import com.roost.dto.SignupRequest;
import com.roost.dto.UserProfileResponse;
import com.roost.exception.ApiException;
import com.roost.model.User;
import com.roost.service.AuthService;
import com.roost.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;

    // 10 failed attempts / 15 min, checked by IP and by the targeted
    // email separately -- either one hitting the cap blocks the request,
    // so an attacker can't dodge the limit by rotating IPs against one
    // account or by trying many accounts from one IP.
    private static final int LOGIN_MAX_ATTEMPTS = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);

    // 5 accounts created / hour / IP. Counts completed signups only --
    // a rejected attempt (e.g. "email already in use") doesn't consume
    // the quota, since that's not the thing this is meant to prevent.
    private static final int SIGNUP_MAX_ACCOUNTS = 5;
    private static final Duration SIGNUP_WINDOW = Duration.ofHours(1);

    public AuthController(AuthService authService, RateLimiterService rateLimiterService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping({"/signup", "/register"})
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        String ipKey = "signup:ip:" + clientIp(httpRequest);
        if (rateLimiterService.isBlocked(ipKey, SIGNUP_MAX_ACCOUNTS, SIGNUP_WINDOW)) {
            throw ApiException.tooManyRequests("Too many accounts created from this network recently. Please try again later.");
        }
        AuthResponse response = authService.signup(request);
        rateLimiterService.record(ipKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        String ipKey = "login:ip:" + clientIp(httpRequest);
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String emailKey = "login:email:" + normalizedEmail;
        if (rateLimiterService.isBlocked(ipKey, LOGIN_MAX_ATTEMPTS, LOGIN_WINDOW)
                || rateLimiterService.isBlocked(emailKey, LOGIN_MAX_ATTEMPTS, LOGIN_WINDOW)) {
            throw ApiException.tooManyRequests("Too many failed login attempts. Please try again in a few minutes.");
        }
        try {
            AuthResponse response = authService.login(request.getEmail(), request.getPassword());
            rateLimiterService.reset(ipKey);
            rateLimiterService.reset(emailKey);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException | ApiException e) {
            // Only genuine "wrong credentials" outcomes count toward the
            // lockout -- authenticationManager.authenticate throws
            // AuthenticationException for a bad password, AuthService
            // throws ApiException for "user not found". Anything else
            // (an unexpected server error) propagates without being
            // recorded, since that's not the caller's fault.
            rateLimiterService.record(ipKey);
            rateLimiterService.record(emailKey);
            throw e;
        }
    }

    /** Real client IP from the leftmost X-Forwarded-For entry -- Railway
     *  sits in front of this app as a proxy, so request.getRemoteAddr()
     *  would just return Railway's internal address, not the caller's. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@RequestBody com.roost.dto.GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(authService.refreshToken(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(authService.getProfile(user));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(@AuthenticationPrincipal User user,
                                                                    @RequestBody Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(authService.updateProfile(user, body));
    }

    /**
     * Upgrades the current account to LANDLORD. This is the only route
     * that ever sets role after signup -- see AuthService.becomeLandlord
     * for why role isn't chosen at registration.
     */
    @PostMapping("/lister-profile")
    public ResponseEntity<UserProfileResponse> becomeLandlord(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(authService.becomeLandlord(user));
    }

    /**
     * Reverts the current account to a plain browsing (TENANT) account.
     * Does not touch or delete existing listings.
     */
    @DeleteMapping("/lister-profile")
    public ResponseEntity<UserProfileResponse> revertToTenant(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(authService.revertToTenant(user));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@AuthenticationPrincipal User user,
                                                                 @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();

        // Accept either naming the frontend has used historically.
        String currentPassword = payload.get("currentPassword");
        if (currentPassword == null) currentPassword = payload.get("oldPassword");
        String newPassword = payload.get("newPassword");
        if (newPassword == null) newPassword = payload.get("password");

        authService.changePassword(user, currentPassword, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
