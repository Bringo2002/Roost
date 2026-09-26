package com.roost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roost.event.GpsVerifiedEvent;
import com.roost.model.Property;
import com.roost.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

import static com.roost.config.AsyncConfig.ENRICHMENT_EXECUTOR;

/**
 * Populates Property.nearbyFacilities out-of-band after GPS verification.
 *
 * This used to run synchronously inside PropertyService.verifyGpsLocation,
 * which meant a landlord verifying their listing's location was stuck
 * waiting on a free, best-effort third-party API (Overpass, up to a 10s
 * timeout) for something that's purely a "nice to have" badge on the
 * listing. Moving it here means:
 *  - the verification request itself returns as soon as the GPS check
 *    and save are done, independent of Overpass's health;
 *  - the listener only fires AFTER the verification transaction commits
 *    (TransactionPhase.AFTER_COMMIT), so it can't race the still-open
 *    write that set gpsVerified/gpsVerifiedAt;
 *  - it runs on a small dedicated pool (see AsyncConfig), not the
 *    request thread, so a slow Overpass response can't tie up a web
 *    worker.
 *
 * Still best-effort: any failure here (Overpass down, JSON error, the
 * property having been deleted in the meantime) is logged and dropped --
 * nearbyFacilities simply stays whatever it was before.
 */
@Component
public class NearbyFacilitiesUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(NearbyFacilitiesUpdateListener.class);

    private final NearbyFacilitiesService nearbyFacilitiesService;
    private final PropertyRepository propertyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NearbyFacilitiesUpdateListener(NearbyFacilitiesService nearbyFacilitiesService,
                                           PropertyRepository propertyRepository) {
        this.nearbyFacilitiesService = nearbyFacilitiesService;
        this.propertyRepository = propertyRepository;
    }

    @Async(ENRICHMENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGpsVerified(GpsVerifiedEvent event) {
        try {
            List<NearbyFacilitiesService.Facility> facilities =
                    nearbyFacilitiesService.findNearby(event.latitude(), event.longitude());

            propertyRepository.findById(event.propertyId()).ifPresent(property -> {
                property.setNearbyFacilities(writeFacilitiesJson(facilities));
                propertyRepository.save(property);
            });
        } catch (Exception e) {
            log.warn("Failed to populate nearbyFacilities for property {}: {}",
                    event.propertyId(), e.getMessage());
        }
    }

    private String writeFacilitiesJson(List<NearbyFacilitiesService.Facility> facilities) {
        try {
            return objectMapper.writeValueAsString(facilities);
        } catch (Exception e) {
            return null;
        }
    }
}
