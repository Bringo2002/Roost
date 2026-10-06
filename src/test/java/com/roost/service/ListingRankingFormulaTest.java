package com.roost.service;

import com.roost.service.ListingRankingFormula.Result;
import com.roost.service.ListingRankingFormula.Signals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests: no Spring, no database. Most assertions are about
 * ordering ("fresher ranks above older, all else equal"), not exact
 * numbers, so retuning the constants does not break them; a single pinned
 * value guards against an accidental change to the formula's shape.
 */
class ListingRankingFormulaTest {

    /** A fully verified, risk-free listing with the given age and activity. */
    private static Signals good(double ageDays, long impressions, long clicks, long saves,
                                long chats, long applications) {
        return new Signals(ageDays, true, true, true, true, 0,
                impressions, clicks, saves, chats, applications);
    }

    private static Signals unverified(double ageDays, long impressions) {
        return new Signals(ageDays, false, false, false, false, 0, impressions, 0, 0, 0, 0);
    }

    private static double scoreOf(Signals s) {
        return ListingRankingFormula.score(s).score();
    }

    @Test
    @DisplayName("pinned example: guards the formula's overall shape")
    void pinnedExample() {
        Result r = ListingRankingFormula.score(good(7, 200, 20, 5, 1, 0));

        assertEquals(0.730292531247, r.score(), 1e-9);
        assertEquals(0.0, r.exposureBoost(), 0.0);
    }

    @Test
    @DisplayName("a fresher listing outranks an older one, all else equal")
    void fresherRanksHigher() {
        assertTrue(scoreOf(good(1, 100, 5, 0, 0, 0)) > scoreOf(good(60, 100, 5, 0, 0, 0)));
    }

    @Test
    @DisplayName("an approved-photo, verified listing outranks an unverified one")
    void qualityRanksHigher() {
        assertTrue(scoreOf(good(5, 100, 5, 0, 0, 0)) > scoreOf(unverified(5, 100)));
    }

    @Test
    @DisplayName("more engagement per impression ranks higher")
    void engagementRanksHigher() {
        assertTrue(scoreOf(good(5, 200, 40, 0, 0, 0)) > scoreOf(good(5, 200, 2, 0, 0, 0)));
    }

    @Test
    @DisplayName("an application is worth more than a click")
    void applicationsWorthMoreThanClicks() {
        assertTrue(scoreOf(good(5, 200, 0, 0, 0, 5)) > scoreOf(good(5, 200, 5, 0, 0, 0)));
    }

    @Test
    @DisplayName("many impressions with no action rank below a never-shown listing (no data is not bad data)")
    void shownWithoutActionRanksBelowUnseen() {
        assertTrue(scoreOf(good(5, 0, 0, 0, 0, 0)) > scoreOf(good(5, 1000, 0, 0, 0, 0)));
    }

    @Test
    @DisplayName("a tiny lucky sample does not beat a large proven one at the same rate")
    void smallSampleIsSmoothed() {
        double lucky = ListingRankingFormula.engagement(good(5, 2, 1, 0, 0, 0));
        double proven = ListingRankingFormula.engagement(good(5, 400, 200, 0, 0, 0));
        assertTrue(proven > lucky);
    }

    @Test
    @DisplayName("exposure boost is largest with no impressions and gone once fully exposed")
    void boostFadesWithExposure() {
        assertEquals(ListingRankingFormula.MAX_EXPOSURE_BOOST,
                ListingRankingFormula.score(good(0, 0, 0, 0, 0, 0)).exposureBoost(), 1e-12);
        double half = ListingRankingFormula.score(good(0, 50, 0, 0, 0, 0)).exposureBoost();
        assertTrue(half > 0 && half < ListingRankingFormula.MAX_EXPOSURE_BOOST);
        assertEquals(0.0, ListingRankingFormula.score(good(0, 100, 0, 0, 0, 0)).exposureBoost(), 0.0);
        assertEquals(0.0, ListingRankingFormula.score(good(0, 5000, 0, 0, 0, 0)).exposureBoost(), 0.0);
    }

    @Test
    @DisplayName("a risk-flagged listing never gets the exposure boost")
    void riskFlaggedGetsNoBoost() {
        Signals flagged = new Signals(0, true, true, true, true, 1, 0, 0, 0, 0, 0);

        assertEquals(0.0, ListingRankingFormula.score(flagged).exposureBoost(), 0.0);
    }

    @Test
    @DisplayName("a low-quality listing never gets the exposure boost")
    void lowQualityGetsNoBoost() {
        assertEquals(0.0, ListingRankingFormula.score(unverified(0, 0)).exposureBoost(), 0.0);
    }

    @Test
    @DisplayName("the boost cannot lift a poor new listing above a good established one")
    void boostCannotRescuePoorListing() {
        Signals poorAndNew = unverified(0, 0);
        Signals goodAndEstablished = good(30, 500, 60, 10, 3, 1);

        assertTrue(scoreOf(goodAndEstablished) > scoreOf(poorAndNew));
    }

    @Test
    @DisplayName("risk flags lower quality, capped so it never goes below zero")
    void riskFlagsLowerQuality() {
        Signals clean = new Signals(5, true, true, true, true, 0, 100, 5, 0, 0, 0);
        Signals flagged = new Signals(5, true, true, true, true, 2, 100, 5, 0, 0, 0);
        Signals manyFlags = new Signals(5, false, false, false, false, 50, 100, 5, 0, 0, 0);

        assertTrue(ListingRankingFormula.quality(clean) > ListingRankingFormula.quality(flagged));
        assertEquals(0.0, ListingRankingFormula.quality(manyFlags), 0.0);
    }

    @Test
    @DisplayName("scores stay finite and bounded for extreme inputs")
    void extremeInputsStayFinite() {
        long huge = Long.MAX_VALUE / 4;
        Signals extreme = new Signals(1e9, true, true, true, true, 1000, huge, huge, huge, huge, huge);

        double score = scoreOf(extreme);

        assertTrue(Double.isFinite(score));
        assertTrue(score >= 0 && score <= 1 + ListingRankingFormula.MAX_EXPOSURE_BOOST);
    }

    @Test
    @DisplayName("invalid signals are rejected before they can reach ORDER BY")
    void invalidSignalsRejected() {
        assertThrows(IllegalArgumentException.class, () -> good(-1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> good(Double.NaN, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> good(Double.POSITIVE_INFINITY, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> good(1, -1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> good(1, 0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Signals(1, true, true, true, true, -1, 0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("a listing with no stored score orders mid-feed: above old poor ones, below good established ones")
    void unscoredScoreSitsMidFeed() {
        double poorOld = scoreOf(unverified(60, 300));
        double goodEstablished = scoreOf(good(10, 500, 100, 20, 5, 2));

        assertTrue(Double.isFinite(ListingRankingFormula.UNSCORED_SCORE));
        assertTrue(ListingRankingFormula.UNSCORED_SCORE > poorOld);
        assertTrue(ListingRankingFormula.UNSCORED_SCORE < goodEstablished);
    }
}
