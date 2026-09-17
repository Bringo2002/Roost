package com.roost.service;

import com.roost.model.Property;
import com.roost.repository.CommunityCheckRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.PropertyReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes tenant-facing caution flags for a listing from data Roost
 * already collects -- report history, community-check responses, and
 * price relative to comparable listings. Deliberately rules-based, no
 * AI call: every signal here is a plain aggregation query, which means
 * it's free to recompute, instant to serve, and fully auditable (a
 * tenant or admin can see exactly why a flag fired, unlike a model's
 * opinion).
 *
 * Mutates and does NOT save [property] -- matches recomputeVerification
 * in PropertyService, which every call site already calls right before
 * its own single save. Called from PropertyService.addProperty,
 * updateProperty, reportProperty, and submitCommunityCheck.
 *
 * Thresholds are @Value-injected from application.properties (risk.*)
 * rather than hardcoded constants, so they can be tuned via a Railway
 * env var once real usage data suggests they need it, without a code
 * change or redeploy.
 */
@Service
public class PropertyRiskService {

    private final double priceOutlierRatio;
    private final int minReportsToFlag;
    private final int minVisitedResponsesForRatio;
    private final double negativeRatioThreshold;
    private final double comparableRadiusKm;

    private final PropertyReportRepository propertyReportRepository;
    private final CommunityCheckRepository communityCheckRepository;
    private final PropertyRepository propertyRepository;

    public PropertyRiskService(PropertyReportRepository propertyReportRepository,
                                CommunityCheckRepository communityCheckRepository,
                                PropertyRepository propertyRepository,
                                @Value("${risk.price-outlier-ratio:0.55}") double priceOutlierRatio,
                                @Value("${risk.min-reports-to-flag:1}") int minReportsToFlag,
                                @Value("${risk.min-visited-responses-for-ratio:3}") int minVisitedResponsesForRatio,
                                @Value("${risk.negative-ratio-threshold:0.5}") double negativeRatioThreshold,
                                @Value("${risk.comparable-radius-km:2.0}") double comparableRadiusKm) {
        this.propertyReportRepository = propertyReportRepository;
        this.communityCheckRepository = communityCheckRepository;
        this.propertyRepository = propertyRepository;
        this.priceOutlierRatio = priceOutlierRatio;
        this.minReportsToFlag = minReportsToFlag;
        this.minVisitedResponsesForRatio = minVisitedResponsesForRatio;
        this.negativeRatioThreshold = negativeRatioThreshold;
        this.comparableRadiusKm = comparableRadiusKm;
    }

    /**
     * Recomputes [property]'s risk flags in place. Always fully
     * replaces the flag list from scratch rather than incrementally
     * patching it, so there's no way for a stale flag to linger after
     * whatever caused it is resolved (e.g. a landlord raising a
     * flagged-as-cheap price on a later edit).
     */
    public void recompute(Property property) {
        List<String> flags = new ArrayList<>();

        long reportCount = propertyReportRepository.countByProperty(property);
        if (reportCount >= minReportsToFlag) {
            flags.add("REPORTED");
        }

        long visited = communityCheckRepository.countVisitedResponses(property);
        if (visited >= minVisitedResponsesForRatio) {
            long inaccurate = communityCheckRepository.countInaccurateVisitedResponses(property);
            if ((double) inaccurate / visited >= negativeRatioThreshold) {
                flags.add("COMMUNITY_INACCURATE");
            }
        }

        ComparableStats comparable = findComparableStats(property);
        if (comparable != null && comparable.average() > 0
                && property.getPrice() < comparable.average() * priceOutlierRatio) {
            flags.add("PRICE_BELOW_MARKET");
        }

        property.setRiskFlags(flags);
    }

    /**
     * Plain price-comparison data for the "is this a fair price?"
     * detail-page feature. Shares the same comparable-listings match
     * the PRICE_BELOW_MARKET flag above uses (see findComparableStats).
     * Returns null when there's nothing to compare against, rather than
     * a comparison with a sample size of zero.
     */
    public record PriceComparison(double averagePrice, int sampleSize, double percentDifference) {}

    public PriceComparison getPriceComparison(Property property) {
        ComparableStats comparable = findComparableStats(property);
        if (comparable == null || comparable.average() <= 0) {
            return null;
        }
        double percentDifference = ((property.getPrice() - comparable.average()) / comparable.average()) * 100;
        return new PriceComparison(comparable.average(), comparable.sampleSize(), percentDifference);
    }

    private record ComparableStats(double average, int sampleSize) {}

    /**
     * Finds comparable listings by GPS distance when [property] has
     * coordinates pinned, falling back to an exact-string location
     * match when it doesn't (or when nothing turns up within
     * comparableRadiusKm -- a listing in a sparsely-covered area
     * shouldn't come back with zero comparables just because its
     * immediate neighbors haven't been GPS-verified yet). The
     * string-match fallback carries its own known limitation: it only
     * finds comparables when listings share identical location text.
     */
    private ComparableStats findComparableStats(Property property) {
        // Brand-new properties (from addProperty, before their first
        // save) have a null id -- a real null passed into "p.id <>
        // :excludeId" would make that comparison evaluate to UNKNOWN
        // for every row in JPQL, silently excluding all comparables
        // rather than just the property itself. -1 can never match a
        // real generated id, so it's a safe sentinel for "nothing to
        // exclude yet."
        long excludeId = property.getId() != null ? property.getId() : -1L;

        Double lat = property.getLatitude();
        Double lng = property.getLongitude();
        if (lat != null && lng != null) {
            Double avg = propertyRepository.findAverageComparablePriceByDistance(
                    property.getHouseType(), property.getBedrooms(), lat, lng, comparableRadiusKm, excludeId);
            if (avg != null) {
                int count = propertyRepository.countComparablePropertiesByDistance(
                        property.getHouseType(), property.getBedrooms(), lat, lng, comparableRadiusKm, excludeId);
                return new ComparableStats(avg, count);
            }
        }

        Double avg = propertyRepository.findAverageComparablePrice(
                property.getHouseType(), property.getBedrooms(), property.getLocation(), excludeId);
        if (avg == null) {
            return null;
        }
        int count = propertyRepository.countComparableProperties(
                property.getHouseType(), property.getBedrooms(), property.getLocation(), excludeId);
        return new ComparableStats(avg, count);
    }
}
