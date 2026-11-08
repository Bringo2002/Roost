package com.roost.service;

import com.roost.service.AddressAreaCheck.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link AddressAreaCheck} with a fake {@link Geocoder}. */
class AddressAreaCheckTest {

    private static final GeocodedPlace KILIMANI =
            new GeocodedPlace("Kilimani, Nairobi", -1.2922, 36.7914, -1.2994, -1.2851, 36.7771, 36.8057);
    private static final GeocodedPlace WESTLANDS =
            new GeocodedPlace("Westlands, Nairobi", -1.2676, 36.8108, -1.2750, -1.2600, 36.7990, 36.8230);

    // a pin inside Kilimani
    private static final double PIN_LAT = -1.2920;
    private static final double PIN_LNG = 36.7900;

    private final List<String> queries = new ArrayList<>();
    private final List<String> countries = new ArrayList<>();

    private AddressAreaCheck checkWith(Geocoder geocoder) {
        return new AddressAreaCheck(geocoder, true, 1000);
    }

    @SafeVarargs
    private Geocoder returning(List<GeocodedPlace>... perQuery) {
        int[] call = {0};
        return (query, country) -> {
            queries.add(query);
            countries.add(country);
            return perQuery[Math.min(call[0]++, perQuery.length - 1)];
        };
    }

    @Test
    @DisplayName("matches when the pin is in the area the address names")
    void matchesWhenInsideTheArea() {
        Outcome outcome = checkWith(returning(List.of(KILIMANI)))
                .check("Kilimani, Nairobi", "KE", PIN_LAT, PIN_LNG);

        assertEquals(Outcome.MATCHES, outcome);
        assertEquals(List.of("KE"), countries, "the listing's country is passed through");
    }

    @Test
    @DisplayName("outside the area when the address is found but the pin is elsewhere")
    void outsideTheArea() {
        Outcome outcome = checkWith(returning(List.of(WESTLANDS)))
                .check("Westlands, Nairobi", "KE", PIN_LAT, PIN_LNG);

        assertEquals(Outcome.OUTSIDE_AREA, outcome);
    }

    @Test
    @DisplayName("not found when the address matches nothing")
    void notFound() {
        Outcome outcome = checkWith(returning(List.of()))
                .check("asdf qwerty", "KE", PIN_LAT, PIN_LNG);

        assertEquals(Outcome.NOT_FOUND, outcome);
    }

    @Test
    @DisplayName("not found for blank text, without calling the geocoder")
    void blankText() {
        AddressAreaCheck check = checkWith(returning(List.of(KILIMANI)));

        assertEquals(Outcome.NOT_FOUND, check.check("  ", "KE", PIN_LAT, PIN_LNG));
        assertEquals(Outcome.NOT_FOUND, check.check(null, "KE", PIN_LAT, PIN_LNG));
        assertEquals(Outcome.NOT_FOUND, check.check(" , ,", "KE", PIN_LAT, PIN_LNG));
        assertTrue(queries.isEmpty());
    }

    @Test
    @DisplayName("unavailable when the geocoder can't be reached, never waved through")
    void unavailable() {
        AddressAreaCheck check = checkWith((query, country) -> {
            throw new GeocodingUnavailableException("down");
        });

        assertEquals(Outcome.UNAVAILABLE, check.check("Kilimani, Nairobi", "KE", PIN_LAT, PIN_LNG));
    }

    @Test
    @DisplayName("skipped, without calling the geocoder, when switched off")
    void skippedWhenDisabled() {
        AddressAreaCheck check = new AddressAreaCheck(returning(List.of(KILIMANI)), false, 1000);

        assertEquals(Outcome.SKIPPED, check.check("Kilimani, Nairobi", "KE", PIN_LAT, PIN_LNG));
        assertTrue(queries.isEmpty());
    }

    @Test
    @DisplayName("a building name the map lacks falls back to its neighbourhood and town")
    void fallsBackToTheLastTwoParts() {
        // first lookup (the full text) finds nothing; the second (last two parts) finds Kilimani
        AddressAreaCheck check = checkWith(returning(List.of(), List.of(KILIMANI)));

        Outcome outcome = check.check("Sunrise Apartments,  Kilimani , Nairobi", "KE", PIN_LAT, PIN_LNG);

        assertEquals(Outcome.MATCHES, outcome);
        assertEquals(List.of("Sunrise Apartments, Kilimani, Nairobi", "Kilimani, Nairobi"), queries);
    }

    @Test
    @DisplayName("a two-part address is looked up once, with no fallback")
    void noFallbackForShortAddresses() {
        checkWith(returning(List.of())).check("Kilimani, Nairobi", "KE", PIN_LAT, PIN_LNG);

        assertEquals(List.of("Kilimani, Nairobi"), queries);
    }

    @Test
    @DisplayName("an address found elsewhere still passes if its neighbourhood contains the pin")
    void fallbackCanStillMatch() {
        AddressAreaCheck check = checkWith(returning(List.of(WESTLANDS), List.of(KILIMANI)));

        assertEquals(Outcome.MATCHES,
                check.check("Sunrise Apartments, Kilimani, Nairobi", "KE", PIN_LAT, PIN_LNG));
    }

    @Test
    @DisplayName("unavailable if the fallback lookup fails after the first found nothing useful")
    void fallbackFailureIsUnavailable() {
        int[] call = {0};
        AddressAreaCheck check = checkWith((query, country) -> {
            if (call[0]++ == 0) {
                return List.of();
            }
            throw new GeocodingUnavailableException("down");
        });

        assertEquals(Outcome.UNAVAILABLE,
                check.check("Sunrise Apartments, Kilimani, Nairobi", "KE", PIN_LAT, PIN_LNG));
    }
}
