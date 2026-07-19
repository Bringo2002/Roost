package com.roost.dto;

import com.roost.model.Role;
import java.time.LocalDateTime;

public class UserProfileResponse {
    private Long id;
    private String name;
    private String email;
    private Role role;
    private String publicKey;
    private LocalDateTime lastActiveAt;

    public UserProfileResponse() {}

    public UserProfileResponse(Long id, String name, String email, Role role, String publicKey, LocalDateTime lastActiveAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.publicKey = publicKey;
        this.lastActiveAt = lastActiveAt;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String name;
        private String email;
        private Role role;
        private String publicKey;
        private LocalDateTime lastActiveAt;

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

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public Builder lastActiveAt(LocalDateTime lastActiveAt) {
            this.lastActiveAt = lastActiveAt;
            return this;
        }

        public UserProfileResponse build() {
            return new UserProfileResponse(id, name, email, role, publicKey, lastActiveAt);
        }
    }
}
