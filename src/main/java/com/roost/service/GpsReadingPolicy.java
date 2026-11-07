package com.roost.service;

import com.roost.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a device location fix is good enough to prove the lister is
 * at a property. Pure apart from its configured thresholds, so every rule can
 * be tested without Spring. The thresholds are configurable because the right
 * values should come from field testing, not guesses.
 *
 * <p>Distance and accuracy are judged together: the phone reports a radius
 * within which it believes it is, so the whole of that circle has to lie
 * inside the tolerance, not just its centre. A weak fix cannot pass by
 * landing near the pin by chance.
 *
 * <p>The reading's timestamp comes from the phone, so it only guards against
 * stale or replayed readings; it is not proof of when the fix was taken.
 */
@Component
public class GpsReadingPolicy {

    /** Why a reading was or wasn't accepted. */
    public enum Outcome {
        PASS,
        /** Missing, non-finite or out-of-range values, or no accuracy reported. */
        INVALID,
        /** Taken too long ago, or timestamped in the future. */
        STALE,
        /** The phone's own accuracy estimate is too coarse to trust. */
        TOO_INACCURATE,
        /** The reading, including its accuracy radius, is too far from the property. */
        TOO_FAR
    }

    /**
     * @param outcome        the decision
     * @param distanceMeters straight-line distance from the property; NaN when the
     *                       reading was rejected before it was measured
     */
    public record Verdict(Outcome outcome, double distanceMeters) {
        public boolean passed() {
            return outcome == Outcome.PASS;
        }
    }

    private final double toleranceMeters;
    private final double maxAccuracyMeters;
    private final long maxAgeMillis;
    private final long maxFutureSkewMillis;

    public GpsReadingPolicy(
            @Value("${roost.gps.tolerance-meters:200}") double toleranceMeters,
            @Value("${roost.gps.max-accuracy-meters:50}") double maxAccuracyMeters,
            @Value("${roost.gps.max-age-seconds:120}") long maxAgeSeconds,
            @Value("${roost.gps.max-future-skew-seconds:60}") long maxFutureSkewSeconds) {
        this.toleranceMeters = toleranceMeters;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.maxAgeMillis = maxAgeSeconds * 1000;
        this.maxFutureSkewMillis = maxFutureSkewSeconds * 1000;
    }

    /**
     * @param propertyLat the property's stored latitude
     * @param propertyLng the property's stored longitude
     * @param reading     the fresh device fix
     * @param nowMillis   the server's current time, epoch milliseconds
     */
    public Verdict evaluate(double propertyLat, double propertyLng, GpsReading reading, long nowMillis) {
        if (!isWellFormed(reading)) {
            return new Verdict(Outcome.INVALID, Double.NaN);
        }
        long ageMillis = nowMillis - reading.capturedAtEpochMs();
        if (ageMillis > maxAgeMillis || ageMillis < -maxFutureSkewMillis) {
            return new Verdict(Outcome.STALE, Double.NaN);
        }
        double distance = GeoUtils.haversineMeters(
                propertyLat, propertyLng, reading.latitude(), reading.longitude());
        if (reading.accuracyMeters() > maxAccuracyMeters) {
            return new Verdict(Outcome.TOO_INACCURATE, distance);
        }
        if (distance + reading.accuracyMeters() > toleranceMeters) {
            return new Verdict(Outcome.TOO_FAR, distance);
        }
        return new Verdict(Outcome.PASS, distance);
    }

    private static boolean isWellFormed(GpsReading r) {
        return r != null
                && Double.isFinite(r.latitude()) && r.latitude() >= -90 && r.latitude() <= 90
                && Double.isFinite(r.longitude()) && r.longitude() >= -180 && r.longitude() <= 180
                && Double.isFinite(r.accuracyMeters()) && r.accuracyMeters() > 0
                && r.capturedAtEpochMs() > 0;
    }
}
