package com.roost.service;

import com.roost.model.Property;
import com.roost.repository.CommunityCheckRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.PropertyReportRepository;
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
 */
@Service
public class PropertyRiskService {

    /** Below this fraction of the comparable-listings average price, a
     *  listing gets flagged -- calibrated to catch "too good to be
     *  true" pricing specifically, not just below-average pricing (a
     *  below-average but honest listing shouldn't get cautioned). */
    private static final double PRICE_OUTLIER_RATIO = 0.55;

    /** Below REPORT_THRESHOLD in PropertyService (which auto-hides the
     *  listing entirely), even a single report is still worth a tenant
     *  knowing about while they decide whether to pursue it. */
    private static final int MIN_REPORTS_TO_FLAG = 1;

    /** Needs a few visited responses before a negative ratio means
     *  anything -- one dissatisfied visitor isn't yet a pattern. */
    private static final int MIN_VISITED_RESPONSES_FOR_RATIO = 3;
    private static final double NEGATIVE_RATIO_THRESHOLD = 0.5;

    private final PropertyReportRepository propertyReportRepository;
    private final CommunityCheckRepository communityCheckRepository;
    private final PropertyRepository propertyRepository;

    public PropertyRiskService(PropertyReportRepository propertyReportRepository,
                                CommunityCheckRepository communityCheckRepository,
                                PropertyRepository propertyRepository) {
        this.propertyReportRepository = propertyReportRepository;
        this.communityCheckRepository = communityCheckRepository;
        this.propertyRepository = propertyRepository;
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
        if (reportCount >= MIN_REPORTS_TO_FLAG) {
            flags.add("REPORTED");
        }

        long visited = communityCheckRepository.countVisitedResponses(property);
        if (visited >= MIN_VISITED_RESPONSES_FOR_RATIO) {
            long inaccurate = communityCheckRepository.countInaccurateVisitedResponses(property);
            if ((double) inaccurate / visited >= NEGATIVE_RATIO_THRESHOLD) {
                flags.add("COMMUNITY_INACCURATE");
            }
        }

        // Brand-new properties (from addProperty, before their first
        // save) have a null id -- a real null passed into "p.id <>
        // :excludeId" would make that comparison evaluate to UNKNOWN
        // for every row in JPQL, silently excluding all comparables
        // rather than just the property itself. -1 can never match a
        // real generated id, so it's a safe sentinel for "nothing to
        // exclude yet."
        long excludeId = property.getId() != null ? property.getId() : -1L;
        Double avgComparablePrice = propertyRepository.findAverageComparablePrice(
                property.getHouseType(), property.getBedrooms(), property.getLocation(), excludeId);
        if (avgComparablePrice != null && avgComparablePrice > 0
                && property.getPrice() < avgComparablePrice * PRICE_OUTLIER_RATIO) {
            flags.add("PRICE_BELOW_MARKET");
        }

        property.setRiskFlags(flags);
    }
}
