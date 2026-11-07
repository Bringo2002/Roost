package com.roost.service;

import com.roost.service.GpsReadingPolicy.Outcome;
import com.roost.util.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rule-by-rule tests for {@link GpsReadingPolicy}; no Spring context needed. */
class GpsReadingPolicyTest {

    private static final double PROP_LAT = -1.2921;
    private static final double PROP_LNG = 36.7872;
    private static final long NOW = 1_800_000_000_000L;

    // tolerance 200 m, max accuracy 50 m, max age 120 s, future skew 60 s
    private final GpsReadingPolicy policy = new GpsReadingPolicy(200, 50, 120, 60);

    private static GpsReading reading(double lat, double lng, double accuracy, long capturedAt) {
        return new GpsReading(lat, lng, accuracy, capturedAt);
    }

    private Outcome outcome(GpsReading r) {
        return policy.evaluate(PROP_LAT, PROP_LNG, r, NOW).outcome();
    }

    @Test
    @DisplayName("a fresh, accurate reading at the property passes")
    void passesAtTheProperty() {
        GpsReadingPolicy.Verdict v = policy.evaluate(
                PROP_LAT, PROP_LNG, reading(PROP_LAT, PROP_LNG, 10, NOW), NOW);

        assertTrue(v.passed());
        assertEquals(0.0, v.distanceMeters(), 0.001);
    }

    @Test
    @DisplayName("a reading from another part of town is too far")
    void rejectsFarAway() {
        assertEquals(Outcome.TOO_FAR, outcome(reading(PROP_LAT + 0.01, PROP_LNG, 10, NOW)));
    }

    @Test
    @DisplayName("the accuracy radius counts: it has to fit inside the tolerance")
    void accuracyRadiusIsPartOfTheDistanceCheck() {
        double offsetLat = PROP_LAT + 0.0016; // roughly 180 m north
        double distance = GeoUtils.haversineMeters(PROP_LAT, PROP_LNG, offsetLat, PROP_LNG);
        assertTrue(distance > 150 && distance < 200, "fixture distance was " + distance);

        // centre is inside 200 m, but a 40 m radius pushes the worst case outside it
        assertEquals(Outcome.TOO_FAR, outcome(reading(offsetLat, PROP_LNG, 40, NOW)));
        assertEquals(Outcome.PASS, outcome(reading(offsetLat, PROP_LNG, 10, NOW)));
    }

    @Test
    @DisplayName("distance plus accuracy exactly at the tolerance still passes")
    void toleranceBoundaryIsInclusive() {
        double distance = GeoUtils.haversineMeters(PROP_LAT, PROP_LNG, PROP_LAT + 0.001, PROP_LNG);
        GpsReadingPolicy exact = new GpsReadingPolicy(distance + 10, 50, 120, 60);

        assertEquals(Outcome.PASS, exact.evaluate(
                PROP_LAT, PROP_LNG, reading(PROP_LAT + 0.001, PROP_LNG, 10, NOW), NOW).outcome());
    }

    @Test
    @DisplayName("a reading less precise than the limit is rejected as too inaccurate")
    void rejectsPoorAccuracy() {
        assertEquals(Outcome.TOO_INACCURATE, outcome(reading(PROP_LAT, PROP_LNG, 50.1, NOW)));
        assertEquals(Outcome.PASS, outcome(reading(PROP_LAT, PROP_LNG, 50, NOW)));
    }

    @Test
    @DisplayName("poor accuracy is reported before distance, since distance can't be trusted")
    void accuracyIsJudgedBeforeDistance() {
        assertEquals(Outcome.TOO_INACCURATE, outcome(reading(PROP_LAT + 0.01, PROP_LNG, 300, NOW)));
    }

    @Test
    @DisplayName("an old reading is stale, a slightly old one is not")
    void rejectsStaleReadings() {
        assertEquals(Outcome.STALE, outcome(reading(PROP_LAT, PROP_LNG, 10, NOW - 121_000)));
        assertEquals(Outcome.PASS, outcome(reading(PROP_LAT, PROP_LNG, 10, NOW - 119_000)));
    }

    @Test
    @DisplayName("a reading timestamped in the future is rejected beyond the clock-skew allowance")
    void rejectsFutureReadings() {
        assertEquals(Outcome.STALE, outcome(reading(PROP_LAT, PROP_LNG, 10, NOW + 61_000)));
        assertEquals(Outcome.PASS, outcome(reading(PROP_LAT, PROP_LNG, 10, NOW + 59_000)));
    }

    @Test
    @DisplayName("missing, non-finite or out-of-range values are invalid")
    void rejectsMalformedReadings() {
        assertEquals(Outcome.INVALID, policy.evaluate(PROP_LAT, PROP_LNG, null, NOW).outcome());
        assertEquals(Outcome.INVALID, outcome(reading(Double.NaN, PROP_LNG, 10, NOW)));
        assertEquals(Outcome.INVALID, outcome(reading(PROP_LAT, Double.POSITIVE_INFINITY, 10, NOW)));
        assertEquals(Outcome.INVALID, outcome(reading(91, PROP_LNG, 10, NOW)));
        assertEquals(Outcome.INVALID, outcome(reading(PROP_LAT, 181, 10, NOW)));
        assertEquals(Outcome.INVALID, outcome(reading(PROP_LAT, PROP_LNG, 10, 0)));
    }

    @Test
    @DisplayName("a reading that reports no accuracy is invalid, not assumed perfect")
    void rejectsMissingAccuracy() {
        assertEquals(Outcome.INVALID, outcome(reading(PROP_LAT, PROP_LNG, 0, NOW)));
        assertEquals(Outcome.INVALID, outcome(reading(PROP_LAT, PROP_LNG, -1, NOW)));
        assertEquals(Outcome.INVALID, outcome(reading(PROP_LAT, PROP_LNG, Double.NaN, NOW)));
    }

    @Test
    @DisplayName("the thresholds are configurable")
    void thresholdsComeFromConfiguration() {
        GpsReadingPolicy strict = new GpsReadingPolicy(50, 20, 30, 10);
        GpsReading offset = reading(PROP_LAT + 0.001, PROP_LNG, 10, NOW); // ~110 m away

        assertEquals(Outcome.PASS, outcome(offset));
        assertEquals(Outcome.TOO_FAR, strict.evaluate(PROP_LAT, PROP_LNG, offset, NOW).outcome());
        assertEquals(Outcome.TOO_INACCURATE, strict.evaluate(
                PROP_LAT, PROP_LNG, reading(PROP_LAT, PROP_LNG, 25, NOW), NOW).outcome());
        assertEquals(Outcome.STALE, strict.evaluate(
                PROP_LAT, PROP_LNG, reading(PROP_LAT, PROP_LNG, 10, NOW - 31_000), NOW).outcome());
    }
}
