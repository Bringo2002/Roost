package com.roost.controller;

import com.roost.dto.UserProfileResponse;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(
                UserProfileResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .phoneVerified(user.isPhoneVerified())
                        .role(user.getRole())
                        .publicKey(user.getPublicKey())
                        .lastActiveAt(user.getLastActiveAt())
                        .build()
        );
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(@AuthenticationPrincipal User user, @RequestBody Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();
        if (body.containsKey("name")) user.setName(body.get("name"));
        if (body.containsKey("phone")) user.setPhone(body.get("phone"));
        userRepository.save(user);
        return ResponseEntity.ok(
                UserProfileResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .phoneVerified(user.isPhoneVerified())
                        .role(user.getRole())
                        .publicKey(user.getPublicKey())
                        .lastActiveAt(user.getLastActiveAt())
                        .build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).build();
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                UserProfileResponse.builder()
                        .id(target.getId())
                        .name(target.getName())
                        .email(target.getEmail())
                        .phone(target.getPhone())
                        .phoneVerified(target.isPhoneVerified())
                        .role(target.getRole())
                        .lastActiveAt(target.getLastActiveAt())
                        .build()
        );
    }

    @PutMapping("/public-key")
    public ResponseEntity<?> setPublicKey(@AuthenticationPrincipal User user, @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        String publicKey = payload.get("publicKey");
        if (publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "publicKey is required"));
        }
        user.setPublicKey(publicKey);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/{id}/public-key")
    public ResponseEntity<?> getPublicKey(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).build();
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("publicKey", target.getPublicKey() == null ? "" : target.getPublicKey()));
    }

    @PostMapping({"/me/change-password", "/change-password"})
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal User user, @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();

        String currentPassword = payload.get("currentPassword");
        if (currentPassword == null) currentPassword = payload.get("oldPassword");
        String newPassword = payload.get("newPassword");
        if (newPassword == null) newPassword = payload.get("password");

        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is required"));
        }
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password is required"));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 6 characters long"));
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
