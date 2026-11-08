package com.roost.service;

import java.util.List;

/**
 * Decides whether a pinned location lies in the area an address names.
 *
 * <p>Compares the pin with the bounding box of each place the geocoder found
 * for the listing's address text, widened by a margin. A box rather than the
 * place's centre point because a neighbourhood's centre can be kilometres from
 * a perfectly legitimate building inside it. Pure, so it can be tested without
 * a network or Spring.
 *
 * <p>Assumes a bounding box does not cross the antimeridian, which holds for
 * every country Roost serves.
 */
public final class AddressAreaPolicy {

    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;

    private AddressAreaPolicy() {
    }

    /**
     * @param pinLat       the pinned latitude
     * @param pinLng       the pinned longitude
     * @param place        a place the geocoder found for the address
     * @param marginMeters how far outside the place's box the pin may be
     */
    public static boolean isWithin(double pinLat, double pinLng, GeocodedPlace place, double marginMeters) {
        double latMargin = marginMeters / METERS_PER_DEGREE_LATITUDE;
        // A degree of longitude shrinks towards the poles; clamp so the margin
        // can't blow up at extreme latitudes.
        double lngMargin = marginMeters
                / (METERS_PER_DEGREE_LATITUDE * Math.max(Math.cos(Math.toRadians(pinLat)), 0.01));
        return pinLat >= place.south() - latMargin
                && pinLat <= place.north() + latMargin
                && pinLng >= place.west() - lngMargin
                && pinLng <= place.east() + lngMargin;
    }

    /** True when the pin is inside any of the places. False for an empty list. */
    public static boolean isWithinAny(double pinLat, double pinLng, List<GeocodedPlace> places, double marginMeters) {
        return places.stream().anyMatch(place -> isWithin(pinLat, pinLng, place, marginMeters));
    }
}
