package com.roost.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Nightly cron job that processes account deletions whose grace period
 * has expired.
 *
 * <p>Runs at 3 AM daily (off-peak, avoids the 9 AM availability task).
 * Delegates the heavy lifting to {@link AccountDeletionService#executeScheduledDeletions()},
 * which queries for expired grace periods and runs the irreversible
 * anonymization sequence on each.
 */
@Service
public class AccountDeletionScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionScheduledTask.class);

    private final AccountDeletionService accountDeletionService;

    public AccountDeletionScheduledTask(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    /**
     * Processes all account deletions whose grace period has expired.
     * Each expired account is anonymized, its listings retired, and
     * its applications hard-deleted.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void processExpiredGracePeriods() {
        log.info("Starting scheduled account deletion sweep");
        int count = accountDeletionService.executeScheduledDeletions();
        log.info("Scheduled account deletion sweep complete: {} account(s) processed", count);
    }
}
