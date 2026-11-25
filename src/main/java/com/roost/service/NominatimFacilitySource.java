package com.roost.service;

import com.roost.service.NearbyFacilitiesService.Facility;
import com.roost.service.PlaceFinder.FoundPlace;
import com.roost.service.PlaceFinder.PlaceKind;
import com.roost.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds a listing's closest mall and hospital through {@link PlaceFinder}
 * (OpenStreetMap's Nominatim, which the server can reach). It is the source
 * the app uses; the Overpass-based {@link NearbyFacilitiesService} is not
 * reachable from the server's address, so nothing was ever stored.
 *
 * <p>It does not find roads: Nominatim cannot list roads by class, so the
 * "closest major road" has to come from another source. A listing's facilities
 * therefore have no road entry for now, and the app already shows only the
 * categories that are present.
 *
 * <p>A lookup is all or nothing: if either search fails, nothing is returned,
 * so a half-answer is never stored as the final one.
 */
@Service
@Primary
public class NominatimFacilitySource implements FacilitySource {

    private static final Logger log = LoggerFactory.getLogger(NominatimFacilitySource.class);

    /** How far from the listing a facility may be and still be worth showing. */
    static final int SEARCH_RADIUS_METERS = 3_000;

    private final PlaceFinder placeFinder;

    public NominatimFacilitySource(PlaceFinder placeFinder) {
        this.placeFinder = placeFinder;
    }

    @Override
    public Optional<List<Facility>> lookup(double lat, double lng) {
        List<Facility> found = new ArrayList<>();
        try {
            nearest(PlaceKind.MALL, "mall", lat, lng).ifPresent(found::add);
            nearest(PlaceKind.HOSPITAL, "hospital", lat, lng).ifPresent(found::add);
        } catch (GeocodingUnavailableException e) {
            log.warn("Nearby places unavailable for ({}, {}): {}", lat, lng, e.getMessage());
            return Optional.empty();
        }
        found.sort(Comparator.comparingDouble(Facility::distanceMeters));
        return Optional.of(found);
    }

    private Optional<Facility> nearest(PlaceKind kind, String category, double lat, double lng)
            throws GeocodingUnavailableException {
        Facility best = null;
        for (FoundPlace place : placeFinder.findNear(kind, lat, lng, SEARCH_RADIUS_METERS)) {
            double distance = GeoUtils.haversineMeters(lat, lng, place.latitude(), place.longitude());
            if (distance > SEARCH_RADIUS_METERS) {
                continue; // the search area is a box, so its corners reach past the radius
            }
            if (best == null || distance < best.distanceMeters()) {
                best = new Facility(place.name(), category, distance, place.latitude(), place.longitude());
            }
        }
        return Optional.ofNullable(best);
    }
}
