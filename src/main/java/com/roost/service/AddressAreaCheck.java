package com.roost.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that a listing's pin is in the area its address text names, using
 * the server's own geocoding of that text. This is what makes a GPS
 * verification mean something: without an independent source for "where the
 * listing says it is", the phone's own reading could be used to vouch for
 * itself.
 *
 * <p>Fails closed. If the address can't be found or the geocoder is down the
 * listing is simply not verified yet; it is never waved through, since an
 * unrecognisable address would otherwise be a way around the check.
 */
@Service
public class AddressAreaCheck {

    /** The result of cross-checking a pin against its listing's address. */
    public enum Outcome {
        /** The pin is in an area the address names. */
        MATCHES,
        /** The address was found, but the pin is not in any area it names. */
        OUTSIDE_AREA,
        /** The address text matches no known place. */
        NOT_FOUND,
        /** The geocoder could not be reached or answered something unreadable; try later. */
        UNAVAILABLE,
        /** The check is switched off by configuration. */
        SKIPPED
    }

    private static final Logger log = LoggerFactory.getLogger(AddressAreaCheck.class);

    private final Geocoder geocoder;
    private final boolean enabled;
    private final double marginMeters;

    public AddressAreaCheck(
            Geocoder geocoder,
            @Value("${roost.geocoding.enabled:true}") boolean enabled,
            @Value("${roost.geocoding.area-margin-meters:1000}") double marginMeters) {
        this.geocoder = geocoder;
        this.enabled = enabled;
        this.marginMeters = marginMeters;
        if (!enabled) {
            log.warn("roost.geocoding.enabled is false: GPS verification is NOT cross-checking the "
                    + "listing's address against its pin.");
        }
    }

    /**
     * @param locationText the listing's address text, e.g. {@code "Sunrise Apartments, Kilimani, Nairobi"}
     * @param countryCode  the listing's country (ISO 3166-1 alpha-2), or null
     * @param pinLat       the pinned latitude
     * @param pinLng       the pinned longitude
     */
    public Outcome check(String locationText, String countryCode, double pinLat, double pinLng) {
        if (!enabled) {
            return Outcome.SKIPPED;
        }
        boolean foundAnyPlace = false;
        for (String query : queriesFor(locationText)) {
            List<GeocodedPlace> places;
            try {
                places = geocoder.search(query, countryCode);
            } catch (GeocodingUnavailableException e) {
                log.warn("Address cross-check unavailable for \"{}\": {}", query, e.getMessage());
                return Outcome.UNAVAILABLE;
            }
            if (AddressAreaPolicy.isWithinAny(pinLat, pinLng, places, marginMeters)) {
                return Outcome.MATCHES;
            }
            foundAnyPlace |= !places.isEmpty();
        }
        return foundAnyPlace ? Outcome.OUTSIDE_AREA : Outcome.NOT_FOUND;
    }

    /**
     * The address as typed, then, when it has three or more comma-separated
     * parts, just its last two (usually "neighbourhood, town"). A specific
     * building name often isn't on the map even when its neighbourhood is, and
     * this keeps honest listers from being stuck on a name OpenStreetMap lacks.
     * At most two lookups, so verification stays quick.
     */
    private static List<String> queriesFor(String locationText) {
        if (locationText == null || locationText.isBlank()) {
            return List.of();
        }
        List<String> parts = Arrays.stream(locationText.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
        if (parts.isEmpty()) {
            return List.of();
        }
        List<String> queries = new ArrayList<>();
        queries.add(String.join(", ", parts));
        if (parts.size() >= 3) {
            queries.add(String.join(", ", parts.subList(parts.size() - 2, parts.size())));
        }
        return queries;
    }
}
