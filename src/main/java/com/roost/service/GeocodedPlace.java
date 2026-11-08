package com.roost.service;

/**
 * A place a geocoder found for an address or area name.
 *
 * @param displayName the geocoder's full name for the place
 * @param latitude    a representative point of the place
 * @param longitude   a representative point of the place
 * @param south       southern edge of the place's bounding box
 * @param north       northern edge of the place's bounding box
 * @param west        western edge of the place's bounding box
 * @param east        eastern edge of the place's bounding box
 */
public record GeocodedPlace(
        String displayName,
        double latitude,
        double longitude,
        double south,
        double north,
        double west,
        double east) {
}
