package com.roost.dto;

import com.roost.model.User;
import java.time.LocalDateTime;

/**
 * A chat participant's identity, as seen by the person they're
 * messaging. Deliberately includes email -- the frontend's message
 * ownership check (chat_room_page.dart: `sender.email == currentUserEmail`)
 * depends on it, so dropping it would break message bubble alignment,
 * not just reduce exposure. Includes phone (contact info both chat
 * participants are meant to exchange) and lastActiveAt (drives the
 * online/last-seen indicator). Deliberately excludes role and
 * savedPropertyIds -- a chat partner has no legitimate need to see
 * what someone else has saved/favorited.
 */
public class ChatUserDto {

    private final Long id;
    private final String name;
    private final String email;
    private final String phone;
    private final LocalDateTime lastActiveAt;

    public ChatUserDto(Long id, String name, String email, String phone, LocalDateTime lastActiveAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.lastActiveAt = lastActiveAt;
    }

    public static ChatUserDto from(User user) {
        if (user == null) {
            return null;
        }
        return new ChatUserDto(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getLastActiveAt());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }
}
