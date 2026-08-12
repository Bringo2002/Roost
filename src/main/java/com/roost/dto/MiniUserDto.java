package com.roost.dto;

import com.roost.model.User;

/**
 * The smallest possible public identity -- id and name only. For
 * contexts with no established need for anything more: a review's
 * author (public, unauthenticated endpoint) and who reacted to a chat
 * message (only ever compared by id client-side; name isn't currently
 * displayed but costs nothing to include for a future "who reacted"
 * tooltip). Not for anywhere a contact channel is needed -- see
 * ChatUserDto for that.
 */
public class MiniUserDto {

    private final Long id;
    private final String name;

    public MiniUserDto(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public static MiniUserDto from(User user) {
        if (user == null) {
            return null;
        }
        return new MiniUserDto(user.getId(), user.getName());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
