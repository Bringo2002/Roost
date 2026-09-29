package com.roost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roost.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Looks up a listing's nearby facilities and stores them.
 *
 * Deliberately NOT @Transactional: the lookup can spend tens of seconds in
 * Overpass retries, and holding a database connection open for that would
 * starve the pool. The database is touched only at the very end, by one
 * short single-statement update.
 *
 * Shared by the after-GPS-verification listener and the backfill job, so
 * both behave identically.
 */
@Component
public class NearbyFacilitiesEnricher {

    private static final Logger log = LoggerFactory.getLogger(NearbyFacilitiesEnricher.class);

    private final NearbyFacilitiesService nearbyFacilitiesService;
    private final PropertyRepository propertyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NearbyFacilitiesEnricher(NearbyFacilitiesService nearbyFacilitiesService,
                                    PropertyRepository propertyRepository) {
        this.nearbyFacilitiesService = nearbyFacilitiesService;
        this.propertyRepository = propertyRepository;
    }

    /**
     * @return true if facilities (possibly an empty list, meaning
     *         "genuinely nothing nearby") were stored; false if Overpass
     *         couldn't answer, in which case NOTHING is written so the
     *         listing stays eligible for the backfill to retry later.
     */
    public boolean enrich(Long propertyId, double lat, double lng) {
        Optional<List<NearbyFacilitiesService.Facility>> facilities = nearbyFacilitiesService.lookup(lat, lng);
        if (facilities.isEmpty()) {
            log.warn("Nearby facilities unavailable for property {}; leaving unset to retry later", propertyId);
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(facilities.get());
            return propertyRepository.updateNearbyFacilities(propertyId, json) > 0;
        } catch (Exception e) {
            log.warn("Failed to store nearby facilities for property {}: {}", propertyId, e.getMessage());
            return false;
        }
    }
}
