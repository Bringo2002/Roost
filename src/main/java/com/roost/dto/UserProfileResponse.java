package com.roost.dto;

import com.roost.model.Role;
import java.time.LocalDateTime;

public class UserProfileResponse {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private boolean phoneVerified;
    private Role role;
    private String publicKey;
    private String avatarUrl;
    private LocalDateTime lastActiveAt;

    /** When the account is scheduled for deletion. Null if no pending
     *  deletion. Only populated for the authenticated user's own profile. */
    private LocalDateTime deletionScheduledAt;

    /** True for accounts created through Google or Apple sign-in, which have
     *  no password the user knows. The app uses it to decide whether the
     *  delete-account screen asks for one. */
    private boolean emailVerified;

    /** True when the account was created with an email + password signup
     *  (as opposed to Google/Apple social sign-in). The app uses it to
     *  decide whether to ask for a password on the delete-account screen. */
    private boolean hasPassword;

    public UserProfileResponse() {}

    public UserProfileResponse(Long id, String name, String email, String phone, boolean phoneVerified, Role role, String publicKey, String avatarUrl, LocalDateTime lastActiveAt, LocalDateTime deletionScheduledAt, boolean emailVerified, boolean hasPassword) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.phoneVerified = phoneVerified;
        this.role = role;
        this.publicKey = publicKey;
        this.avatarUrl = avatarUrl;
        this.lastActiveAt = lastActiveAt;
        this.deletionScheduledAt = deletionScheduledAt;
        this.emailVerified = emailVerified;
        this.hasPassword = hasPassword;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public LocalDateTime getDeletionScheduledAt() {
        return deletionScheduledAt;
    }

    public void setDeletionScheduledAt(LocalDateTime deletionScheduledAt) {
        this.deletionScheduledAt = deletionScheduledAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isHasPassword() {
        return hasPassword;
    }

    public void setHasPassword(boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private boolean phoneVerified;
        private Role role;
        private String publicKey;
        private String avatarUrl;
        private LocalDateTime lastActiveAt;
        private LocalDateTime deletionScheduledAt;
        private boolean emailVerified;
        private boolean hasPassword;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder phoneVerified(boolean phoneVerified) {
            this.phoneVerified = phoneVerified;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public Builder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        public Builder lastActiveAt(LocalDateTime lastActiveAt) {
            this.lastActiveAt = lastActiveAt;
            return this;
        }

        public Builder deletionScheduledAt(LocalDateTime deletionScheduledAt) {
            this.deletionScheduledAt = deletionScheduledAt;
            return this;
        }

        public Builder emailVerified(boolean emailVerified) {
            this.emailVerified = emailVerified;
            return this;
        }

        public Builder hasPassword(boolean hasPassword) {
            this.hasPassword = hasPassword;
            return this;
        }

        public UserProfileResponse build() {
            return new UserProfileResponse(id, name, email, phone, phoneVerified, role, publicKey, avatarUrl, lastActiveAt, deletionScheduledAt, emailVerified, hasPassword);
        }
    }
}
