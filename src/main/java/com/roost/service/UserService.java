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
    private final FirebasePhoneAuthService firebasePhoneAuthService;
    private final PropertyService propertyService;

    public UserService(UserRepository userRepository, FirebasePhoneAuthService firebasePhoneAuthService, PropertyService propertyService) {
        this.userRepository = userRepository;
        this.firebasePhoneAuthService = firebasePhoneAuthService;
        this.propertyService = propertyService;
    }

    /**
     * Public-facing view of another user's profile. Deliberately omits
     * email and phone -- those are contact details, not public profile
     * info, and this endpoint is reachable for any authenticated user by
     * ID. (It also omits publicKey, which is only exposed via the
     * dedicated GET /{id}/public-key endpoint.) phoneVerified is kept
     * since it's just the "Verified" badge signal, not the number itself.
     */
    public UserProfileResponse getUserProfile(Long id) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return UserProfileResponse.builder()
                .id(target.getId())
                .name(target.getName())
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

    /**
     * Verifies a Firebase phone-auth ID token (the client completes the
     * SMS OTP flow itself; this confirms it actually happened rather than
     * trusting the client's word for it) and marks the phone verified.
     * Also re-checks the "Verified" badge on every listing this user
     * owns, since phone verification is one of the three signals that
     * compose it.
     */
    public String verifyPhone(User user, String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw ApiException.badRequest("idToken is required");
        }
        String phoneNumber = firebasePhoneAuthService.verifyPhoneToken(idToken);
        if (phoneNumber == null) {
            throw ApiException.badRequest("Could not verify phone number. Please try again.");
        }
        user.setPhone(phoneNumber);
        user.setPhoneVerified(true);
        userRepository.save(user);
        propertyService.recomputeVerificationForOwner(user);
        return phoneNumber;
    }
}
