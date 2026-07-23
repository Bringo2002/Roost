package com.roost.controller;

import com.roost.dto.AuthRequest;
import com.roost.dto.AuthResponse;
import com.roost.dto.SignupRequest;
import com.roost.dto.UserProfileResponse;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping({"/signup", "/register"})
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email is already in use.");
        }

        var user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.TENANT);
        userRepository.save(user);

        var jwtToken = jwtService.generateToken(user);
        return ResponseEntity.ok(AuthResponse.builder().token(jwtToken).build());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        var jwtToken = jwtService.generateToken(user);
        return ResponseEntity.ok(AuthResponse.builder().token(jwtToken).build());
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

    @PostMapping("/verify-phone")
    public ResponseEntity<?> verifyPhone(@AuthenticationPrincipal User user, @RequestBody(required = false) Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();
        user.setPhoneVerified(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Phone verified successfully"));
    }
}
