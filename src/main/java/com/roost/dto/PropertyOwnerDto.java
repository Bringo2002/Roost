package com.roost.dto;

import com.roost.model.User;
import java.time.LocalDateTime;

/**
 * Public-safe view of a property's owner, embedded in PropertyResponseDto.
 * Deliberately excludes email and every other User field -- the frontend
 * only ever reads id/name/role/lastActiveAt off property.owner (for the
 * chat screen and the online-status dot), so nothing else belongs here.
 * See PropertyResponseDto for why this exists instead of serializing
 * the User entity directly.
 */
public class PropertyOwnerDto {

    private final Long id;
    private final String name;
    private final String role;
    private final String avatarUrl;
    private final LocalDateTime lastActiveAt;
    private final String responseTime;

    public PropertyOwnerDto(Long id, String name, String role, String avatarUrl, LocalDateTime lastActiveAt) {
        this(id, name, role, avatarUrl, lastActiveAt, calculateResponseTime(lastActiveAt));
    }

    public PropertyOwnerDto(Long id, String name, String role, String avatarUrl, LocalDateTime lastActiveAt, String responseTime) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.avatarUrl = avatarUrl;
        this.lastActiveAt = lastActiveAt;
        this.responseTime = responseTime;
    }

    public static PropertyOwnerDto from(User owner) {
        if (owner == null) {
            return null;
        }
        return new PropertyOwnerDto(
                owner.getId(),
                owner.getName(),
                owner.getRole() != null ? owner.getRole().name() : null,
                owner.getAvatarUrl(),
                owner.getLastActiveAt(),
                calculateResponseTime(owner.getLastActiveAt())
        );
    }

    private static String calculateResponseTime(LocalDateTime lastActiveAt) {
        if (lastActiveAt != null) {
            java.time.Duration diff = java.time.Duration.between(lastActiveAt, LocalDateTime.now());
            long minutes = Math.abs(diff.toMinutes());
            if (minutes <= 30) {
                return "Usually responds within 15 minutes";
            } else if (minutes <= 120) {
                return "Usually responds within 1 hour";
            } else if (minutes <= 720) {
                return "Usually responds within a few hours";
            }
        }
        return "Usually responds within 1 hour";
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public String getResponseTime() {
        return responseTime;
    }
}
