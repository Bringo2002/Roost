package com.roost.model;

/**
 * Kinds of tenant behaviour recorded in {@link ListingEvent}. Persisted as
 * the enum name in a plain VARCHAR column (not a JPA {@code @Enumerated}
 * column) so adding a value later never requires touching a database
 * CHECK constraint -- see the {@code users_role_check} history in
 * {@code DatabaseSchemaMigrator} for why that matters here.
 */
public enum ListingEventType {
    /** A listing card was actually seen (visible long enough), not merely fetched. */
    IMPRESSION,
    /** The listing detail page was opened. */
    CLICK,
    /** The listing was saved/favourited. */
    SAVE,
    /** A chat with the landlord was started from the listing. */
    CHAT_STARTED,
    /** A rental application was submitted for the listing. */
    APPLICATION
}
