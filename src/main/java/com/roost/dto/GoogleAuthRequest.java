package com.roost.dto;

import com.roost.model.Role;

public class GoogleAuthRequest {
    /** Firebase ID token from the client's Google sign-in. */
    private String idToken;

    /** Only used the first time this Google account signs in (account
     *  creation); ignored for an existing account. Defaults to TENANT
     *  if omitted, matching the email/password signup default. */
    private Role role;

    public GoogleAuthRequest() {}

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
