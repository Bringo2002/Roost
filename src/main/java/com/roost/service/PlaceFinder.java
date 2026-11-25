package com.roost.service;

import java.util.List;

/**
 * Finds named places of one kind (a hospital, a shopping mall) around a
 * point. An interface so the provider can be swapped (a self-hosted copy of
 * the map data, a paid service) without touching the callers.
 */
public interface PlaceFinder {

    /** The kinds of place that can be searched for. */
    enum PlaceKind {
        HOSPITAL,
        MALL
    }

    /**
     * A named place the provider found.
     *
     * @param name      the place's name
     * @param latitude  where it is
     * @param longitude where it is
     */
    record FoundPlace(String name, double latitude, double longitude) {
    }

    /**
     * @param kind         what to look for
     * @param latitude     the centre of the search
     * @param longitude    the centre of the search
     * @param radiusMeters how far out to look; results may include places a
     *                     little beyond this (the search area is a box), so
     *                     callers needing a hard limit measure distances
     * @return the places found, in no particular order; empty when there are
     *         none
     * @throws GeocodingUnavailableException when the lookup could not be
     *                                       completed, which is not the same
     *                                       as "nothing nearby"
     */
    List<FoundPlace> findNear(PlaceKind kind, double latitude, double longitude, int radiusMeters)
            throws GeocodingUnavailableException;
}
