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
