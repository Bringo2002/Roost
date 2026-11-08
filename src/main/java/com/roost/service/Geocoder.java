package com.roost.service;

import java.util.List;

/**
 * Turns an address or area name into the places it could refer to. An
 * interface so the provider can be swapped (a self-hosted Nominatim, a paid
 * service) without touching the callers.
 */
public interface Geocoder {

    /**
     * @param query       the address or area text, e.g. {@code "Kilimani, Nairobi"}
     * @param countryCode an ISO 3166-1 alpha-2 code to restrict the search to, or null
     * @return the matching places, best match first; empty when nothing matches
     * @throws GeocodingUnavailableException when the lookup could not be completed
     */
    List<GeocodedPlace> search(String query, String countryCode) throws GeocodingUnavailableException;
}
