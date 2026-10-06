package com.roost.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppleAuthServiceTest {

    private static Map<String, Object> claimsFor(String provider) {
        Map<String, Object> firebase = new HashMap<>();
        firebase.put("sign_in_provider", provider);
        Map<String, Object> claims = new HashMap<>();
        claims.put("firebase", firebase);
        return claims;
    }

    @Test
    @DisplayName("apple.com token with verified email -> identity")
    void validAppleTokenYieldsIdentity() {
        AppleAuthService.AppleIdentity id =
                AppleAuthService.toIdentity(claimsFor("apple.com"), "a@b.com", true, "  Ada Lovelace ");

        assertNotNull(id);
        assertEquals("a@b.com", id.email());
        assertEquals("Ada Lovelace", id.name());
    }

    @Test
    @DisplayName("token from another provider is rejected (no cross-provider replay)")
    void wrongProviderRejected() {
        assertNull(AppleAuthService.toIdentity(claimsFor("google.com"), "a@b.com", true, "Ada"));
        assertNull(AppleAuthService.toIdentity(claimsFor("password"), "a@b.com", true, "Ada"));
    }

    @Test
    @DisplayName("missing firebase claim or null claims is rejected")
    void missingFirebaseClaimRejected() {
        assertNull(AppleAuthService.toIdentity(new HashMap<>(), "a@b.com", true, "Ada"));
        assertNull(AppleAuthService.toIdentity(null, "a@b.com", true, "Ada"));
    }

    @Test
    @DisplayName("missing, blank or unverified email is rejected")
    void emailRulesEnforced() {
        assertNull(AppleAuthService.toIdentity(claimsFor("apple.com"), null, true, "Ada"));
        assertNull(AppleAuthService.toIdentity(claimsFor("apple.com"), "  ", true, "Ada"));
        assertNull(AppleAuthService.toIdentity(claimsFor("apple.com"), "a@b.com", false, "Ada"));
    }

    @Test
    @DisplayName("absent or blank name -> identity with null name (Apple often omits it)")
    void missingNameIsNull() {
        assertNull(AppleAuthService.toIdentity(claimsFor("apple.com"), "a@b.com", true, null).name());
        assertNull(AppleAuthService.toIdentity(claimsFor("apple.com"), "a@b.com", true, "   ").name());
    }

    @Test
    @DisplayName("unconfigured service never verifies anything and never throws")
    void unconfiguredReturnsNull() {
        AppleAuthService service = new AppleAuthService("");

        assertFalse(service.isConfigured());
        assertNull(service.verifyAppleToken("any-token"));
        assertNull(service.verifyAppleToken(null));
    }
}
