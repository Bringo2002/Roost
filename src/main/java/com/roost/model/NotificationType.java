package com.roost.model;

/**
 * Canonical push notification types sent in the {@code data.type} field of
 * every FCM message. Using an enum prevents typo-induced mismatches between
 * the server and the mobile client's notification router.
 */
public enum NotificationType {

    CHAT("chat"),
    PHOTO_APPROVED("photo_approved"),
    PHOTO_REJECTED("photo_rejected"),
    DOCUMENTS_APPROVED("documents_approved"),
    LISTING_UNDER_REVIEW("listing_under_review"),
    LISTING_KEPT_HIDDEN("listing_kept_hidden"),
    LISTING_RESTORED("listing_restored"),
    LISTING_NEEDS_CHANGES("listing_needs_changes"),
    AVAILABILITY_CHECK("availability_check"),
    ACCOUNT_DISABLED("account_disabled"),
    ACCOUNT_LOCKED("account_locked"),
    IDENTITY_VERIFIED("identity_verified"),
    IDENTITY_REJECTED("identity_rejected"),
    APPLICATION_RECEIVED("application_received"),
    APPLICATION_STATUS_UPDATE("application_status_update");

    private final String key;

    NotificationType(String key) {
        this.key = key;
    }

    /** The wire-format string sent inside the FCM data payload. */
    public String key() {
        return key;
    }
}
