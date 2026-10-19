package com.roost.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link User}'s account state methods (M2).
 *
 * <p>Previously {@code isEnabled()} and {@code isAccountNonLocked()} both
 * hardcoded {@code true}. These tests verify the fields are respected.
 * Written RED-first but will already pass once Layer 1 fields are in place —
 * they serve as a regression guard.
 */
class UserAccountStateTest {

    @Test
    @DisplayName("new user is enabled and unlocked by default")
    void defaultsToEnabledAndUnlocked() {
        User u = new User();
        assertTrue(u.isEnabled());
        assertTrue(u.isAccountNonLocked());
    }

    @Test
    @DisplayName("setEnabled(false) makes isEnabled() return false")
    void disabledUserIsNotEnabled() {
        User u = new User();
        u.setEnabled(false);
        assertFalse(u.isEnabled());
    }

    @Test
    @DisplayName("setAccountNonLocked(false) makes isAccountNonLocked() return false")
    void lockedUserIsNotNonLocked() {
        User u = new User();
        u.setAccountNonLocked(false);
        assertFalse(u.isAccountNonLocked());
    }

    @Test
    @DisplayName("re-enabling a disabled user restores isEnabled()")
    void reEnablingRestoresEnabled() {
        User u = new User();
        u.setEnabled(false);
        u.setEnabled(true);
        assertTrue(u.isEnabled());
    }

    @Test
    @DisplayName("unlocking a locked user restores isAccountNonLocked()")
    void unlockingRestoresNonLocked() {
        User u = new User();
        u.setAccountNonLocked(false);
        u.setAccountNonLocked(true);
        assertTrue(u.isAccountNonLocked());
    }
}
