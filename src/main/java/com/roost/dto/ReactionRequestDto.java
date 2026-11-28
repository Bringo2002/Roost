package com.roost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Typed request body for {@code POST /api/chat/react/{messageId}}
 * (toggle a reaction on a message).
 */
public class ReactionRequestDto {

    @NotBlank(message = "emoji is required")
    @Size(max = 10, message = "emoji exceeds maximum length")
    private String emoji;

    public ReactionRequestDto() {}

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
}
