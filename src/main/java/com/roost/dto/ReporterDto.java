package com.roost.dto;

import com.roost.model.User;

/**
 * Identity of whoever filed a property report, for admins reviewing
 * flagged listings. Deliberately includes email (unlike
 * PropertyOwnerDto) -- admins need to recognize repeat/abusive
 * reporters, and this only ever reaches an ADMIN-role-checked endpoint
 * (see AdminController), not the general browsing surface.
 */
public class ReporterDto {

    private final Long id;
    private final String name;
    private final String email;

    public ReporterDto(Long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public static ReporterDto from(User user) {
        if (user == null) {
            return null;
        }
        return new ReporterDto(user.getId(), user.getName(), user.getEmail());
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
}
