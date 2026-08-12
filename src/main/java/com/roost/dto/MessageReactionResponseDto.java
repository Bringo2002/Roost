package com.roost.dto;

import com.roost.model.MessageReaction;

public class MessageReactionResponseDto {

    private final Long id;
    private final MiniUserDto user;
    private final String emoji;

    public MessageReactionResponseDto(MessageReaction r) {
        this.id = r.getId();
        this.user = MiniUserDto.from(r.getUser());
        this.emoji = r.getEmoji();
    }

    public static MessageReactionResponseDto from(MessageReaction r) {
        return r == null ? null : new MessageReactionResponseDto(r);
    }

    public Long getId() {
        return id;
    }

    public MiniUserDto getUser() {
        return user;
    }

    public String getEmoji() {
        return emoji;
    }
}
