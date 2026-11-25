package com.roost.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code source} on {@link ListingEvent}: no database needed. */
class ListingEventSourceTest {

    @Test
    @DisplayName("every source name fits the source column")
    void everySourceNameFitsTheColumn() {
        for (ListingEventSource source : ListingEventSource.values()) {
            assertTrue(source.name().length() <= ListingEvent.SOURCE_MAX_LENGTH,
                    source + " is longer than the source column");
        }
    }

    @Test
    @DisplayName("an event stores the name of the source it was given")
    void storesTheSourceName() {
        ListingEvent event = new ListingEvent(null, 7L, null, ListingEventType.CLICK, Instant.now(),
                ListingEventSource.SEARCH_RECOMMENDED);

        assertEquals("SEARCH_RECOMMENDED", event.getSource());
    }

    @Test
    @DisplayName("the original constructor and a null source both leave the source unset")
    void sourceDefaultsToNull() {
        ListingEvent withoutSource = new ListingEvent(null, 7L, null, ListingEventType.CLICK, Instant.now());
        ListingEvent nullSource = new ListingEvent(null, 7L, null, ListingEventType.CLICK, Instant.now(), null);

        assertNull(withoutSource.getSource());
        assertNull(nullSource.getSource());
    }
}
