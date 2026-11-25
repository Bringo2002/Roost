package com.roost.model;

/**
 * The feed surface that showed a listing when a {@link ListingEvent}
 * happened. Persisted as the enum name in a plain VARCHAR column, like
 * {@link ListingEventType}. Absent (null) on events from before this column
 * existed and from clients that do not report one.
 */
public enum ListingEventSource {
    /** The home feed's nearby listings (distance-sorted when location is known, else newest). */
    HOME_NEARBY,
    /** The home feed's personalised "For You" listings. */
    HOME_FOR_YOU,
    /** Search results in the default order. */
    SEARCH,
    /** Search results with the "Recommended Order" switch on. */
    SEARCH_RECOMMENDED
}
