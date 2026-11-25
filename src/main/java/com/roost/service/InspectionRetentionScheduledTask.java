package com.roost.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Nightly cron job that deletes inspection records past their retention
 * period. Runs at 3:45 AM, after the account-deletion sweep (3:00) and the
 * file purge (3:30).
 */
@Service
public class InspectionRetentionScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(InspectionRetentionScheduledTask.class);

    private final InspectionRetentionService retentionService;

    public InspectionRetentionScheduledTask(InspectionRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    /** Deletes every inspection past its retention period. */
    @Scheduled(cron = "0 45 3 * * *")
    public void deleteExpiredInspections() {
        int deleted = retentionService.deleteExpired();
        log.info("Scheduled inspection retention complete: {} deleted", deleted);
    }
}
