package com.roost.controller;

import com.roost.dto.AuthRequest;
import com.roost.dto.AuthResponse;
import com.roost.dto.SignupRequest;
import com.roost.dto.UserProfileResponse;
import com.roost.model.User;
import com.roost.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping({"/signup", "/register"})
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request.getEmail(), request.getPassword()));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@RequestBody com.roost.dto.GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
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

    @PostMapping("/verify-phone")
    public ResponseEntity<Map<String, String>> verifyPhone(@AuthenticationPrincipal User user,
                                                              @RequestBody(required = false) Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();
        authService.verifyPhone(user);
        return ResponseEntity.ok(Map.of("message", "Phone verified successfully"));
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
