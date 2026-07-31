package com.roost.dto;

public class GoogleAuthRequest {
    /** Firebase ID token from the client's Google sign-in. */
    private String idToken;

    public GoogleAuthRequest() {}

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
