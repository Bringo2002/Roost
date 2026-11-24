package com.roost.service;

import com.roost.model.PendingFileDeletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Nightly cron job that deletes the stored files queued when accounts were
 * erased, once their 30-day retention period is over.
 *
 * <p>Runs at 3:30 AM, after the 3 AM account-deletion sweep, so files queued
 * by that sweep are on the queue before this run starts (they are not due
 * yet; they become due 30 days later). Each file is handled on its own, so a
 * failure on one is logged and retried the next night without holding up
 * the others.
 */
@Service
public class FilePurgeScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(FilePurgeScheduledTask.class);

    private final FilePurgeService filePurgeService;

    public FilePurgeScheduledTask(FilePurgeService filePurgeService) {
        this.filePurgeService = filePurgeService;
    }

    /** Deletes every queued file whose retention period is over. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeDueFiles() {
        List<PendingFileDeletion> due = filePurgeService.findDue();
        int deleted = 0;
        int kept = 0;
        int failed = 0;

        for (PendingFileDeletion entry : due) {
            try {
                FilePurgeService.Outcome outcome = filePurgeService.purge(entry);
                if (outcome == FilePurgeService.Outcome.DELETED) {
                    deleted++;
                } else if (outcome == FilePurgeService.Outcome.KEPT_IN_USE) {
                    kept++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not delete queued {} file {}; it stays queued and is retried tomorrow",
                        entry.getBucket(), entry.getStorageKey(), e);
            }
        }

        log.info("Scheduled file purge complete: {} deleted, {} kept because still in use, {} failed",
                deleted, kept, failed);
    }
}
