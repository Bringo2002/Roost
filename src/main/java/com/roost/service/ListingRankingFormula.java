package com.roost.service;

/**
 * Pure scoring function behind {@code sort=recommended}: turns what is
 * known about one listing into a single number, higher = shown earlier.
 * No database, clock or Spring dependency, so every rule below can be
 * unit-tested and tuned in isolation; a scheduled job feeds it signals and
 * stores the result.
 *
 * <p>Score = {@code 0.35*quality + 0.30*freshness + 0.35*engagement + exposureBoost}.
 *
 * <ul>
 *   <li><b>quality</b> (0..1): approved photos, verification, GPS and
 *       community verification, reduced by risk flags.</li>
 *   <li><b>freshness</b> (0..1]: exponential decay with age, so new
 *       listings start strong without old ones vanishing outright.</li>
 *   <li><b>engagement</b> (0..1): weighted actions per impression, smoothed
 *       toward a prior so a listing with 2 impressions and 1 click does not
 *       outrank one with 400 impressions and 40 clicks.</li>
 *   <li><b>exposureBoost</b> (0..{@link #MAX_EXPOSURE_BOOST}): a capped lift
 *       for listings that have barely been shown, so new listings get a
 *       fair chance to earn engagement. Withheld from low-quality or
 *       risk-flagged listings, and small enough that it cannot lift a
 *       poor listing above a good, well-exposed one.</li>
 * </ul>
 *
 * The constants are starting guesses, not measured values; expect to tune
 * them once real event data exists.
 */
public final class ListingRankingFormula {

    // --- top-level weights (sum to 1.0) ---
    static final double QUALITY_WEIGHT = 0.35;
    static final double FRESHNESS_WEIGHT = 0.30;
    static final double ENGAGEMENT_WEIGHT = 0.35;

    // --- freshness ---
    /** Freshness falls to 1/e (~37%) after this many days. */
    static final double FRESHNESS_DECAY_DAYS = 14.0;

    // --- quality ---
    static final double PHOTO_APPROVED_POINTS = 0.40;
    static final double VERIFIED_POINTS = 0.30;
    static final double GPS_VERIFIED_POINTS = 0.20;
    static final double COMMUNITY_VERIFIED_POINTS = 0.10;
    static final double RISK_FLAG_PENALTY = 0.15;
    static final double MAX_RISK_PENALTY = 0.45;

    // --- engagement ---
    static final double CLICK_WEIGHT = 1.0;
    static final double SAVE_WEIGHT = 3.0;
    static final double CHAT_WEIGHT = 5.0;
    static final double APPLICATION_WEIGHT = 8.0;
    /** Weighted actions per impression assumed for a listing with no history. */
    static final double PRIOR_RATE = 0.05;
    /** How many impressions the prior counts as: higher = slower to trust new data. */
    static final double PRIOR_IMPRESSIONS = 50.0;
    /** A smoothed rate at or above this scores the full 1.0. */
    static final double TARGET_RATE = 0.30;

    // --- exposure boost ---
    static final double MAX_EXPOSURE_BOOST = 0.25;
    /** Impressions in the window after which a listing counts as fully exposed. */
    static final double TARGET_IMPRESSIONS = 100.0;
    /** Listings below this quality never receive a boost. */
    static final double MIN_QUALITY_FOR_BOOST = 0.50;

    private ListingRankingFormula() {}

    /**
     * What the formula knows about a listing. Event counts cover one
     * trailing window (e.g. 7 days); the caller chooses the window.
     *
     * @param ageDays        days since the listing was published, never negative
     * @param riskFlagCount  number of risk flags raised on the listing
     */
    public record Signals(
            double ageDays,
            boolean photoApproved,
            boolean verified,
            boolean gpsVerified,
            boolean communityVerified,
            int riskFlagCount,
            long impressions,
            long clicks,
            long saves,
            long chats,
            long applications) {

        public Signals {
            if (!Double.isFinite(ageDays) || ageDays < 0) {
                throw new IllegalArgumentException("ageDays must be a finite number >= 0");
            }
            if (riskFlagCount < 0 || impressions < 0 || clicks < 0 || saves < 0
                    || chats < 0 || applications < 0) {
                throw new IllegalArgumentException("counts must not be negative");
            }
        }
    }

    /** @param score the ordering value (includes {@code exposureBoost}) */
    public record Result(double score, double exposureBoost) {}

    public static Result score(Signals s) {
        double quality = quality(s);
        double freshness = Math.exp(-s.ageDays() / FRESHNESS_DECAY_DAYS);
        double engagement = engagement(s);
        double boost = exposureBoost(s, quality);

        double base = QUALITY_WEIGHT * quality
                + FRESHNESS_WEIGHT * freshness
                + ENGAGEMENT_WEIGHT * engagement;
        return new Result(base + boost, boost);
    }

    static double quality(Signals s) {
        double points = (s.photoApproved() ? PHOTO_APPROVED_POINTS : 0)
                + (s.verified() ? VERIFIED_POINTS : 0)
                + (s.gpsVerified() ? GPS_VERIFIED_POINTS : 0)
                + (s.communityVerified() ? COMMUNITY_VERIFIED_POINTS : 0);
        double penalty = Math.min(MAX_RISK_PENALTY, s.riskFlagCount() * RISK_FLAG_PENALTY);
        return clamp01(points - penalty);
    }

    static double engagement(Signals s) {
        double weightedActions = CLICK_WEIGHT * s.clicks()
                + SAVE_WEIGHT * s.saves()
                + CHAT_WEIGHT * s.chats()
                + APPLICATION_WEIGHT * s.applications();
        double smoothedRate = (weightedActions + PRIOR_RATE * PRIOR_IMPRESSIONS)
                / (s.impressions() + PRIOR_IMPRESSIONS);
        return clamp01(smoothedRate / TARGET_RATE);
    }

    static double exposureBoost(Signals s, double quality) {
        if (s.riskFlagCount() > 0 || quality < MIN_QUALITY_FOR_BOOST) {
            return 0;
        }
        double deficit = clamp01(1.0 - s.impressions() / TARGET_IMPRESSIONS);
        return MAX_EXPOSURE_BOOST * deficit;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
