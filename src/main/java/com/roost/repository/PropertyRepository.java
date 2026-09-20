package com.roost.repository;

import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByOwner(User owner);

    List<Property> findByIdIn(Collection<Long> ids);

    /** Listings due for the 7-day "still available?" reminder -- no
     *  reminder sent yet, and it's been long enough since last confirmed. */
    List<Property> findByAvailableTrueAndRemindedAtIsNullAndLastConfirmedAtBefore(LocalDateTime threshold);

    /** Listings whose reminder grace period has expired with no response. */
    List<Property> findByAvailableTrueAndRemindedAtIsNotNullAndRemindedAtBefore(LocalDateTime threshold);

    List<Property> findByPhotoApprovedFalse();

    List<Property> findByStatus(String status);

    /**
     * Average price among published listings with the same house type,
     * bedroom count, and location string -- the comparable set used to
     * decide whether a given listing's price is a "too good to be true"
     * outlier (see PropertyRiskService). Uses plain AVG rather than a
     * true median (which JPQL doesn't support portably) -- a reasonable
     * v1 tradeoff; the location match is exact-string, so it only finds
     * comparables when listings share identical location text, which is
     * a known limitation worth revisiting if location data turns out to
     * be too inconsistent for this to fire in practice. Returns null
     * when there are no comparables, not zero.
     */
    @Query("SELECT AVG(p.price) FROM Property p WHERE p.status = 'PUBLISHED' " +
           "AND p.houseType = :houseType AND p.bedrooms = :bedrooms " +
           "AND p.location = :location AND p.id <> :excludeId")
    Double findAverageComparablePrice(
            @Param("houseType") String houseType,
            @Param("bedrooms") int bedrooms,
            @Param("location") String location,
            @Param("excludeId") Long excludeId);

    /** Sample size behind findAverageComparablePrice -- same WHERE
     *  clause, used so the "fair price?" UI can say how many listings
     *  the comparison is actually based on. */
    @Query("SELECT COUNT(p) FROM Property p WHERE p.status = 'PUBLISHED' " +
           "AND p.houseType = :houseType AND p.bedrooms = :bedrooms " +
           "AND p.location = :location AND p.id <> :excludeId")
    int countComparableProperties(
            @Param("houseType") String houseType,
            @Param("bedrooms") int bedrooms,
            @Param("location") String location,
            @Param("excludeId") Long excludeId);

    /**
     * GPS-distance version of findAverageComparablePrice -- fixes the
     * exact-string location matching limitation noted above by finding
     * comparables within [radiusKm] of (lat, lng) instead. Used
     * whenever the subject property has coordinates pinned; the
     * string-match version above remains the fallback for the many
     * properties that don't yet (see PropertyRiskService, which tries
     * this first and falls back). Only matches other properties that
     * themselves have coordinates, same reasoning as
     * findByStatusPublishedPagedSortedByDistance.
     */
    @Query("SELECT AVG(p.price) FROM Property p WHERE p.status = 'PUBLISHED' " +
           "AND p.houseType = :houseType AND p.bedrooms = :bedrooms " +
           "AND p.id <> :excludeId AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL " +
           "AND (6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) <= :radiusKm")
    Double findAverageComparablePriceByDistance(
            @Param("houseType") String houseType,
            @Param("bedrooms") int bedrooms,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm,
            @Param("excludeId") Long excludeId);

    /** Sample size behind findAverageComparablePriceByDistance -- same
     *  WHERE clause, same reasoning as countComparableProperties. */
    @Query("SELECT COUNT(p) FROM Property p WHERE p.status = 'PUBLISHED' " +
           "AND p.houseType = :houseType AND p.bedrooms = :bedrooms " +
           "AND p.id <> :excludeId AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL " +
           "AND (6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) <= :radiusKm")
    int countComparablePropertiesByDistance(
            @Param("houseType") String houseType,
            @Param("bedrooms") int bedrooms,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm,
            @Param("excludeId") Long excludeId);

    /**
     * Entity-returning counterparts of the two queries above, for
     * RentEstimateService -- that needs the actual comparable listings
     * (to check each one's amenity flags for value-driver analysis),
     * not just aggregate price stats. Capped via [pageable] since an
     * unbounded comparable set in a dense area could otherwise pull
     * an unreasonable number of full entities into memory for what's
     * an internal computation step, not a user-facing list.
     */
    @Query("SELECT p FROM Property p WHERE p.status = 'PUBLISHED' " +
           "AND p.houseType = :houseType AND p.bedrooms = :bedrooms " +
           "AND p.location = :location AND p.id <> :excludeId")
    List<Property> findComparableProperties(
            @Param("houseType") String houseType,
            @Param("bedrooms") int bedrooms,
            @Param("location") String location,
            @Param("excludeId") Long excludeId,
            Pageable pageable);

    @Query("SELECT p FROM Property p WHERE p.status = 'PUBLISHED' " +
           "AND p.houseType = :houseType AND p.bedrooms = :bedrooms " +
           "AND p.id <> :excludeId AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL " +
           "AND (6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) <= :radiusKm")
    List<Property> findComparablePropertiesByDistance(
            @Param("houseType") String houseType,
            @Param("bedrooms") int bedrooms,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm,
            @Param("excludeId") Long excludeId,
            Pageable pageable);

    /**
     * Paginated version of findByStatus("PUBLISHED") -- same scope as the
     * unpaginated feed (no availability or coordinate requirement; an
     * unavailable listing still shows with a "Taken" badge client-side,
     * and a listing without pinned coordinates still shows, just without
     * a distance label). Newest-first, matching the /filter endpoint's
     * own newest-first default when no location is given.
     */
    @Query("SELECT p FROM Property p WHERE p.status = 'PUBLISHED' ORDER BY p.id DESC")
    List<Property> findByStatusPublishedPaged(Pageable pageable);

    /**
     * Same as findByStatusPublishedPaged, but ordered by distance from
     * (lat, lng) when the client has a device location. Deliberately
     * does NOT require p.latitude/p.longitude to be non-null the way
     * filterPropertiesSortedByDistance does -- this is the general
     * browse feed, not a location search, so a listing missing
     * coordinates still needs to appear (just sorted to the end, via the
     * CASE fallback below, rather than silently dropped).
     */
    @Query("SELECT p FROM Property p WHERE p.status = 'PUBLISHED' " +
           "ORDER BY (CASE WHEN p.latitude IS NULL OR p.longitude IS NULL THEN 999999 ELSE " +
           "(6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) END) ASC")
    List<Property> findByStatusPublishedPagedSortedByDistance(
            @Param("lat") double lat,
            @Param("lng") double lng,
            Pageable pageable);

    /**
     * The lat/lng BETWEEN bounds are a cheap square-shaped pre-filter
     * (computed in PropertyService.getNearby, always a superset of the
     * true circle) applied before the expensive trig distance calc, so
     * rows well outside the search radius never reach acos/cos/sin at
     * all. The trig expression itself still runs twice per remaining
     * row (WHERE + ORDER BY) -- JPQL can't alias it for reuse without
     * switching this off entities onto a DTO/tuple projection -- but
     * that's now happening over a small candidate set instead of every
     * published listing in the database. Results are identical to the
     * previous query; this only changes how many rows pay for the trig
     * math to get there.
     */
    @Query("SELECT p FROM Property p WHERE " +
           "p.status = 'PUBLISHED' AND " +
           "p.available = true AND " +
           "p.latitude IS NOT NULL AND p.longitude IS NOT NULL AND " +
           "p.latitude BETWEEN :minLat AND :maxLat AND " +
           "p.longitude BETWEEN :minLng AND :maxLng AND " +
           "(6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) < :radiusKm " +
           "ORDER BY (6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) ASC")
    List<Property> findNearby(@Param("lat") double lat,
                               @Param("lng") double lng,
                               @Param("radiusKm") double radiusKm,
                               @Param("minLat") double minLat,
                               @Param("maxLat") double maxLat,
                               @Param("minLng") double minLng,
                               @Param("maxLng") double maxLng);

    @Query("SELECT p FROM Property p WHERE " +
           "p.status = 'PUBLISHED' AND " +
           "p.available = true AND " +
           "(:houseType IS NULL OR p.houseType = :houseType) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
           "(:bedrooms IS NULL OR p.bedrooms >= :bedrooms) AND " +
           "(:furnished IS NULL OR p.furnished = :furnished) AND " +
           "(:parking IS NULL OR p.parking = :parking) AND " +
           "(:wifi IS NULL OR p.wifi = :wifi) AND " +
           "(:water IS NULL OR p.water = :water) AND " +
           "(:security IS NULL OR p.security = :security) AND " +
           "(:verified IS NULL OR p.verified = :verified)")
    List<Property> filterProperties(
            @Param("houseType") String houseType,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("bedrooms") Integer bedrooms,
            @Param("furnished") Boolean furnished,
            @Param("parking") Boolean parking,
            @Param("wifi") Boolean wifi,
            @Param("water") Boolean water,
            @Param("security") Boolean security,
            @Param("verified") Boolean verified);

    /**
     * Paginated variant of filterProperties, used once a client asks for
     * a specific page (search_page.dart's infinite scroll) instead of
     * the full unbounded result set. Same WHERE clause as above, just
     * with an explicit ORDER BY (required for stable paging -- without
     * one, which rows land on which page isn't guaranteed to stay
     * consistent as the table changes between requests) and a Pageable
     * for LIMIT/OFFSET. Newest-first, since that's the useful default
     * for a listings feed and there's no location to sort by here.
     */
    @Query("SELECT p FROM Property p WHERE " +
           "p.status = 'PUBLISHED' AND " +
           "p.available = true AND " +
           "(:houseType IS NULL OR p.houseType = :houseType) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
           "(:bedrooms IS NULL OR p.bedrooms >= :bedrooms) AND " +
           "(:furnished IS NULL OR p.furnished = :furnished) AND " +
           "(:parking IS NULL OR p.parking = :parking) AND " +
           "(:wifi IS NULL OR p.wifi = :wifi) AND " +
           "(:water IS NULL OR p.water = :water) AND " +
           "(:security IS NULL OR p.security = :security) AND " +
           "(:verified IS NULL OR p.verified = :verified) " +
           "ORDER BY p.id DESC")
    List<Property> filterProperties(
            @Param("houseType") String houseType,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("bedrooms") Integer bedrooms,
            @Param("furnished") Boolean furnished,
            @Param("parking") Boolean parking,
            @Param("wifi") Boolean wifi,
            @Param("water") Boolean water,
            @Param("security") Boolean security,
            @Param("verified") Boolean verified,
            Pageable pageable);

    /**
     * Same filters again, but sorted by distance from (lat, lng) instead
     * of newest-first, and paginated -- used when the client has a
     * device location. No bounding-box pre-filter here unlike findNearby,
     * because this has no search radius to bound against; it's paging
     * through every matching listing sorted by distance, not "listings
     * within Xkm".
     */
    @Query("SELECT p FROM Property p WHERE " +
           "p.status = 'PUBLISHED' AND " +
           "p.available = true AND " +
           "(:houseType IS NULL OR p.houseType = :houseType) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
           "(:bedrooms IS NULL OR p.bedrooms >= :bedrooms) AND " +
           "(:furnished IS NULL OR p.furnished = :furnished) AND " +
           "(:parking IS NULL OR p.parking = :parking) AND " +
           "(:wifi IS NULL OR p.wifi = :wifi) AND " +
           "(:water IS NULL OR p.water = :water) AND " +
           "(:security IS NULL OR p.security = :security) AND " +
           "(:verified IS NULL OR p.verified = :verified) AND " +
           "p.latitude IS NOT NULL AND p.longitude IS NOT NULL " +
           "ORDER BY (6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) ASC")
    List<Property> filterPropertiesSortedByDistance(
            @Param("houseType") String houseType,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("bedrooms") Integer bedrooms,
            @Param("furnished") Boolean furnished,
            @Param("parking") Boolean parking,
            @Param("wifi") Boolean wifi,
            @Param("water") Boolean water,
            @Param("security") Boolean security,
            @Param("verified") Boolean verified,
            @Param("lat") double lat,
            @Param("lng") double lng,
            Pageable pageable);
}
