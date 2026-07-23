package com.roost.service;

import com.roost.model.Property;
import com.roost.repository.PropertyRepository;
import org.slf.Logger;
import org.slf.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AvailabilityScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityScheduledTask.class);
    private final PropertyRepository propertyRepository;

    public AvailabilityScheduledTask(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Scheduled(cron = "0 0 9 * * *") // 9 AM daily
    @Transactional
    public void checkExpiredListings() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Property> expired = propertyRepository.findByAvailableTrueAndLastConfirmedAtBefore(sevenDaysAgo);
        for (Property p : expired) {
            p.setAvailable(false);
            log.info("Listing {} ({}) marked unavailable due to inactivity (>7 days since confirmation)", p.getId(), p.getTitle());
        }
        if (!expired.isEmpty()) {
            propertyRepository.saveAll(expired);
        }
    }
}
