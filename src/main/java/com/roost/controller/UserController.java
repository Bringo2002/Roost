package com.roost.controller;

import com.roost.dto.UserProfileResponse;
import com.roost.model.User;
import com.roost.service.AuthService;
import com.roost.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    // /me and /change-password below intentionally reuse AuthService rather
    // than duplicating that logic here -- they're the same operations
    // AuthController exposes under /api/auth, just also mounted under
    // /api/users for backward compatibility with the frontend.
    private final AuthService authService;
    private final UserService userService;

    public UserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
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

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(userService.getUserProfile(id));
    }

    @PutMapping("/public-key")
    public ResponseEntity<Map<String, String>> setPublicKey(@AuthenticationPrincipal User user,
                                                               @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        userService.setPublicKey(user, payload.get("publicKey"));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** Registers/updates this device's FCM token for push notifications. */
    @PostMapping("/me/fcm-token")
    public ResponseEntity<Map<String, String>> setDeviceToken(@AuthenticationPrincipal User user,
                                                                 @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        userService.setDeviceToken(user, payload.get("fcmToken"));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Verifies a Firebase phone-auth ID token the client obtained after
     * completing the SMS OTP flow, and marks the phone verified.
     */
    @PostMapping("/me/verify-phone")
    public ResponseEntity<Map<String, String>> verifyPhone(@AuthenticationPrincipal User user,
                                                              @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        String phone = userService.verifyPhone(user, payload.get("idToken"));
        return ResponseEntity.ok(Map.of("status", "ok", "phone", phone));
    }

    @GetMapping("/{id}/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("publicKey", userService.getPublicKey(id)));
    }

    @PostMapping({"/me/change-password", "/change-password"})
    public ResponseEntity<Map<String, String>> changePassword(@AuthenticationPrincipal User user,
                                                                  @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();

        String currentPassword = payload.get("currentPassword");
        if (currentPassword == null) currentPassword = payload.get("oldPassword");
        String newPassword = payload.get("newPassword");
        if (newPassword == null) newPassword = payload.get("password");

        authService.changePassword(user, currentPassword, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
