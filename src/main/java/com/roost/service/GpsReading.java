package com.roost.service;

/**
 * A device location fix as reported by the lister's phone.
 *
 * @param latitude        degrees, -90..90
 * @param longitude       degrees, -180..180
 * @param accuracyMeters  the phone's own horizontal accuracy estimate (radius), in metres
 * @param capturedAtEpochMs when the phone says the fix was taken, in epoch milliseconds
 */
public record GpsReading(double latitude, double longitude, double accuracyMeters, long capturedAtEpochMs) {
}
