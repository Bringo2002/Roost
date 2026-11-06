package com.roost.util;

import java.util.Locale;

/**
 * The one definition of a canonical email: trimmed and lowercased.
 *
 * Every place that reads or writes a user's email goes through this, so
 * "Ada@X.com " and "ada@x.com" are always the same account. The users table
 * enforces the same rule with a CHECK constraint (migration V4), which means
 * a code path that forgets to normalize fails loudly instead of quietly
 * creating a duplicate account.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {}

    /** @return the canonical form, or {@code null} when {@code email} is null */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
