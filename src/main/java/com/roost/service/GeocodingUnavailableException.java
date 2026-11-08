package com.roost.service;

/**
 * A geocoding lookup could not be completed: the service was unreachable,
 * rate limiting us, or answered something unreadable. Distinct from "the
 * address matches nothing", which is a normal, empty result.
 */
public class GeocodingUnavailableException extends Exception {

    public GeocodingUnavailableException(String message) {
        super(message);
    }

    public GeocodingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
