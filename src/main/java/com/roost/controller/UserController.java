package com.roost.controller;

import com.roost.dto.UserProfileResponse;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                UserProfileResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .publicKey(user.getPublicKey())
                        .lastActiveAt(user.getLastActiveAt())
                        .build()
        );
    }

    /**
     * Looks up another user's basic profile -- used by the chat UI to
     * show and refresh online / last-seen status for a conversation
     * partner. Same fields already visible via /api/chat/active and
     * message sender/recipient objects; this just adds a way to fetch
     * them for a specific id without depending on message history.
     */
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
                        .role(target.getRole())
                        .lastActiveAt(target.getLastActiveAt())
                        .build()
        );
    }

    /**
     * Uploads (or replaces) the caller's X25519 public key for end-to-end
     * encrypted messaging. Re-uploading — e.g. after a reinstall that
     * generated a new local keypair — is allowed, but note that any
     * messages encrypted under the old key will no longer be decryptable
     * by this account; that's an inherent tradeoff of E2EE, not a bug.
     */
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

    /**
     * Looks up another user's public key so the caller can encrypt a
     * message to them. Returns null if that user hasn't uploaded one yet.
     */
    @GetMapping("/{id}/public-key")
    public ResponseEntity<?> getPublicKey(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).build();
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("publicKey", target.getPublicKey() == null ? "" : target.getPublicKey()));
    }
}
