package com.roost.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthResponse {
    private String token;

    /** True only for a brand-new account created via Google sign-in, so
     *  the client can route to onboarding instead of home -- mirrors the
     *  signup-vs-login navigation split already used for email/password.
     *  Always false for plain signup/login, which don't set it. */
    private boolean isNewUser;

    public AuthResponse() {}

    public AuthResponse(String token) {
        this(token, false);
    }

    public AuthResponse(String token, boolean isNewUser) {
        this.token = token;
        this.isNewUser = isNewUser;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /** Serialized as {@code isNewUser}. Without the annotation Jackson derives
     *  the name from the getter and emits {@code newUser}, which is not the key
     *  clients read. */
    @JsonProperty("isNewUser")
    public boolean isNewUser() {
        return isNewUser;
    }

    public void setNewUser(boolean isNewUser) {
        this.isNewUser = isNewUser;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String token;
        private boolean isNewUser;

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder isNewUser(boolean isNewUser) {
            this.isNewUser = isNewUser;
            return this;
        }

        public AuthResponse build() {
            return new AuthResponse(token, isNewUser);
        }
    }
}
