package com.roost.dto;

import com.roost.model.UserBlock;

import java.time.LocalDateTime;

/**
 * One entry in a user's blocked list: just enough to show who they blocked
 * and let them undo it. Deliberately has no email or phone.
 */
public class BlockedUserDto {

    private final Long id;
    private final String name;
    private final String avatarUrl;
    private final LocalDateTime blockedAt;

    public BlockedUserDto(Long id, String name, String avatarUrl, LocalDateTime blockedAt) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.blockedAt = blockedAt;
    }

    /**
     * Builds the entry for a block; the blocked user must already be loaded.
     *
     * @param block the block to describe
     * @return the DTO, or null if {@code block} is null
     */
    public static BlockedUserDto from(UserBlock block) {
        if (block == null) {
            return null;
        }
        return new BlockedUserDto(block.getBlocked().getId(), block.getBlocked().getName(),
                block.getBlocked().getAvatarUrl(), block.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }
}
