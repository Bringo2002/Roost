package com.roost.service;

import com.roost.model.Property;
import com.roost.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Implements the brief's availability-confirmation flow: every 7 days, a
 * landlord is asked (via push) whether a listing is still available; if
 * they don't respond (confirm, or mark rented) within roughly 24 hours,
 * the listing is hidden. Runs once daily, so the actual grace window is
 * 24-48h depending on where a listing falls relative to the 9AM run --
 * generous rather than punitive is the right default here.
 */
@Service
public class AvailabilityScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityScheduledTask.class);
    private final PropertyRepository propertyRepository;
    private final FirebasePushService firebasePushService;

    public AvailabilityScheduledTask(PropertyRepository propertyRepository, FirebasePushService firebasePushService) {
        this.propertyRepository = propertyRepository;
        this.firebasePushService = firebasePushService;
    }

    @Scheduled(cron = "0 0 9 * * *") // 9 AM daily
    @Transactional
    public void checkExpiredListings() {
        sendReminders();
        expireUnconfirmed();
    }

    private void sendReminders() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Property> due = propertyRepository.findByAvailableTrueAndRemindedAtIsNullAndLastConfirmedAtBefore(sevenDaysAgo);

        for (Property p : due) {
            p.setRemindedAt(LocalDateTime.now());
            if (p.getOwner() != null) {
                firebasePushService.sendToUser(
                        p.getOwner(),
                        "Is this property still available?",
                        p.getTitle() + " -- confirm or mark as rented in the app",
                        Map.of("type", "availability_check", "propertyId", String.valueOf(p.getId()))
                );
            }
            log.info("Sent availability reminder for listing {} ({})", p.getId(), p.getTitle());
        }
        if (!due.isEmpty()) {
            propertyRepository.saveAll(due);
        }
    }

    private void expireUnconfirmed() {
        LocalDateTime gracePeriodEnd = LocalDateTime.now().minusHours(24);
        List<Property> expired = propertyRepository.findByAvailableTrueAndRemindedAtIsNotNullAndRemindedAtBefore(gracePeriodEnd);

        for (Property p : expired) {
            p.setAvailable(false);
            p.setRemindedAt(null);
            log.info("Listing {} ({}) hidden -- no response to availability reminder", p.getId(), p.getTitle());
        }
        if (!expired.isEmpty()) {
            propertyRepository.saveAll(expired);
        }
    }
}
