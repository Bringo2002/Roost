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
    private final LocalDateTime lastActiveAt;

    public PropertyOwnerDto(Long id, String name, String role, LocalDateTime lastActiveAt) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.lastActiveAt = lastActiveAt;
    }

    public static PropertyOwnerDto from(User owner) {
        if (owner == null) {
            return null;
        }
        return new PropertyOwnerDto(
                owner.getId(),
                owner.getName(),
                owner.getRole() != null ? owner.getRole().name() : null,
                owner.getLastActiveAt()
        );
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

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }
}
