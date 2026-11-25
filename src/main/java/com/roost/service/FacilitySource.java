package com.roost.service;

import java.util.List;
import java.util.Optional;

/**
 * Where a listing's nearby places (the closest mall, hospital and so on) come
 * from. An interface so the data source can change without touching the code
 * that stores the result.
 */
public interface FacilitySource {

    /**
     * @return the facilities near the point, nearest first; an empty list
     *         when there genuinely are none; {@link Optional#empty()} when
     *         the lookup could not be completed, so the caller must not store
     *         it as "nothing nearby" and should try again later
     */
    Optional<List<NearbyFacilitiesService.Facility>> lookup(double lat, double lng);
}
