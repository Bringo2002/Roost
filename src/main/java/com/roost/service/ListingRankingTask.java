package com.roost.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Refreshes listing ranking scores on a timer. The work lives in
 * {@link ListingRankingService}; this only schedules it and makes sure a
 * failed run is logged rather than thrown into the scheduler.
 *
 * Config (all optional):
 *   roost.ranking.enabled           default true
 *   roost.ranking.interval-ms       default 3600000 (1 hour)
 *   roost.ranking.initial-delay-ms  default 300000 (5 min)
 *   roost.ranking.window-days       default 7 (see ListingRankingService)
 */
@Component
public class ListingRankingTask {

    private static final Logger log = LoggerFactory.getLogger(ListingRankingTask.class);

    private final ListingRankingService rankingService;
    private final boolean enabled;

    public ListingRankingTask(
            ListingRankingService rankingService,
            @Value("${roost.ranking.enabled:true}") boolean enabled) {
        this.rankingService = rankingService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${roost.ranking.interval-ms:3600000}",
            initialDelayString = "${roost.ranking.initial-delay-ms:300000}")
    public void run() {
        if (!enabled) return;
        try {
            int scored = rankingService.recompute(Clock.systemDefaultZone());
            log.info("Listing ranking: scored {} listings", scored);
        } catch (Exception e) {
            log.warn("Listing ranking: run failed, will retry next interval: {}", e.getMessage());
        }
    }
}
