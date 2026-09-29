package com.roost.service;

import com.roost.event.GpsVerifiedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.roost.config.AsyncConfig.ENRICHMENT_EXECUTOR;

/**
 * Populates Property.nearbyFacilities right after GPS verification, off the
 * request thread (see AsyncConfig) and only once the verification
 * transaction has committed (AFTER_COMMIT), so the verification request
 * itself never waits on Overpass.
 *
 * Best-effort: if Overpass can't answer even after retries, nothing is
 * stored and NearbyFacilitiesBackfillTask picks the listing up later.
 * The work itself lives in NearbyFacilitiesEnricher.
 */
@Component
public class NearbyFacilitiesUpdateListener {

    private final NearbyFacilitiesEnricher enricher;

    public NearbyFacilitiesUpdateListener(NearbyFacilitiesEnricher enricher) {
        this.enricher = enricher;
    }

    @Async(ENRICHMENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGpsVerified(GpsVerifiedEvent event) {
        enricher.enrich(event.propertyId(), event.latitude(), event.longitude());
    }
}
