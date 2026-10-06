package com.roost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/auth/apple}. */
public class AppleAuthRequest {

    /** Firebase ID token from the client's Sign in with Apple. */
    @NotBlank
    private String idToken;

    /**
     * Optional display name. Apple only shares the user's name with the app
     * on the very first authorization, so the client forwards it here for
     * use when the verified token carries none.
     */
    @Size(max = 100)
    private String name;

    public AppleAuthRequest() {}

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
