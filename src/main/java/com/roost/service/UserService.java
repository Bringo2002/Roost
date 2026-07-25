package com.roost.service;

import com.roost.dto.UserProfileResponse;
import com.roost.exception.ApiException;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Public-facing view of another user's profile. Deliberately omits
     * publicKey -- that's only exposed via the dedicated
     * GET /{id}/public-key endpoint.
     */
    public UserProfileResponse getUserProfile(Long id) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return UserProfileResponse.builder()
                .id(target.getId())
                .name(target.getName())
                .email(target.getEmail())
                .phone(target.getPhone())
                .phoneVerified(target.isPhoneVerified())
                .role(target.getRole())
                .lastActiveAt(target.getLastActiveAt())
                .build();
    }

    public void setPublicKey(User user, String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw ApiException.badRequest("publicKey is required");
        }
        user.setPublicKey(publicKey);
        userRepository.save(user);
    }

    public String getPublicKey(Long id) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return target.getPublicKey() == null ? "" : target.getPublicKey();
    }

    /** Registers/updates this device's FCM token for push notifications. */
    public void setDeviceToken(User user, String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("fcmToken is required");
        }
        user.setDeviceToken(token);
        userRepository.save(user);
    }
}
