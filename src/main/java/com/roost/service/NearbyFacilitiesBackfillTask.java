package com.roost.service;

import com.roost.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fills in nearby facilities for GPS-verified listings that never got them:
 * everything verified before the feature existed, plus any listing whose
 * post-verification lookup failed (Overpass down).
 *
 * Works through a small batch per run, one listing at a time with a pause
 * between lookups -- Overpass is a free shared service, so this stays well
 * inside its fair-use limits -- and stops the run early if lookups keep
 * failing, rather than hammering an unavailable service. When nothing is
 * left the run is a single cheap query.
 *
 * Safe if two app instances run it at once: the update is idempotent.
 *
 * Config (all optional):
 *   roost.facilities.backfill.enabled      default true
 *   roost.facilities.backfill.batch-size   default 20
 *   roost.facilities.backfill.pause-ms     default 2000
 *   roost.facilities.backfill.interval-ms  default 900000 (15 min)
 *   roost.facilities.backfill.initial-delay-ms default 120000
 */
@Component
public class NearbyFacilitiesBackfillTask {

    private static final Logger log = LoggerFactory.getLogger(NearbyFacilitiesBackfillTask.class);

    /** Give up on this run after this many failures in a row. */
    static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final PropertyRepository propertyRepository;
    private final NearbyFacilitiesEnricher enricher;
    private final boolean enabled;
    private final int batchSize;
    private final long pauseMillis;

    public NearbyFacilitiesBackfillTask(
            PropertyRepository propertyRepository,
            NearbyFacilitiesEnricher enricher,
            @Value("${roost.facilities.backfill.enabled:true}") boolean enabled,
            @Value("${roost.facilities.backfill.batch-size:20}") int batchSize,
            @Value("${roost.facilities.backfill.pause-ms:2000}") long pauseMillis) {
        this.propertyRepository = propertyRepository;
        this.enricher = enricher;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.pauseMillis = pauseMillis;
    }

    @Scheduled(
            fixedDelayString = "${roost.facilities.backfill.interval-ms:900000}",
            initialDelayString = "${roost.facilities.backfill.initial-delay-ms:120000}")
    public void run() {
        if (!enabled) return;

        List<PropertyRepository.GeoPoint> batch;
        try {
            batch = propertyRepository.findGpsVerifiedMissingNearbyFacilities(PageRequest.of(0, batchSize));
        } catch (Exception e) {
            log.warn("Nearby-facilities backfill: could not load batch: {}", e.getMessage());
            return;
        }
        if (batch.isEmpty()) return;

        int stored = 0;
        int consecutiveFailures = 0;
        for (PropertyRepository.GeoPoint point : batch) {
            boolean ok = enricher.enrich(point.getId(), point.getLatitude(), point.getLongitude());
            if (ok) {
                stored++;
                consecutiveFailures = 0;
            } else if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.warn("Nearby-facilities backfill: {} lookups failed in a row, pausing until next run",
                        consecutiveFailures);
                break;
            }
            if (!pause()) break;
        }
        log.info("Nearby-facilities backfill: stored {} of {} attempted (batch of {})",
                stored, batch.size(), batchSize);
    }

    /** @return false if interrupted (shutdown), true otherwise. */
    private boolean pause() {
        if (pauseMillis <= 0) return true;
        try {
            Thread.sleep(pauseMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
