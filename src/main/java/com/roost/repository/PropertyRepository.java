package com.roost.repository;

import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByOwner(User owner);

    /** Backs GET /my-listings, which maps every result through
     *  PropertyResponseDto (and therefore reads .getOwner() on each row)
     *  -- fetch-join the owner so a landlord's dashboard costs one query
     *  instead of one plus one owner SELECT per listing. */
    @EntityGraph(attributePaths = "owner")
    List<Property> findByOwnerOrderByIdDesc(User owner);

    /** Backs GET /api/v2/properties/my-listings (the paginated, slim-DTO
     *  version of /my-listings). Returns a Slice, not a Page: infinite scroll
     *  only needs "is there another page?", and Slice answers that by
     *  fetching size+1 rows instead of running a second COUNT(*) query.
     *
     *  `filter` mirrors the dashboard's filter chips and is applied here,
     *  not client-side, because the client only ever holds the pages it has
     *  scrolled through. The predicates below MUST stay identical to the
     *  ones in {@link #countOwnerListings} so a chip's badge count always
     *  equals the number of rows that chip lists:
     *    ALL       every listing
     *    PUBLISHED status PUBLISHED and still available
     *    DRAFT     status DRAFT
     *    RENTED    not available
     *
     *  Ordering: drafts first (they need the landlord's attention), then
     *  newest first; id DESC is the tiebreaker that makes offset paging
     *  deterministic. The owner is fetch-joined (a to-one join, so it can't
     *  multiply rows or force in-memory paging) to avoid one owner SELECT per
     *  row. Callers must pass an unsorted Pageable -- the ORDER BY lives here.
     *
     *  The slim DTO reads no @ElementCollection fields, so no collection
     *  loading is triggered at all. */
    @Query("SELECT p FROM Property p JOIN FETCH p.owner "
            + "WHERE p.owner = :owner AND ("
            + "  :filter = 'ALL' "
            + "  OR (:filter = 'PUBLISHED' AND p.status = 'PUBLISHED' AND p.available = true) "
            + "  OR (:filter = 'DRAFT' AND p.status = 'DRAFT') "
            + "  OR (:filter = 'RENTED' AND p.available = false)) "
            + "ORDER BY CASE WHEN p.status = 'DRAFT' THEN 0 ELSE 1 END, p.id DESC")
    Slice<Property> findOwnerListingsPage(@Param("owner") User owner,
                                          @Param("filter") String filter,
                                          Pageable pageable);

    /** Per-bucket totals for the dashboard's stats header and filter-chip
     *  badges, computed over ALL of an owner's listings in one aggregate
     *  query. With pagination the client can no longer count what it has
     *  loaded -- it would only ever see the pages scrolled so far. Any field
     *  can be null when the owner has no listings (SUM over zero rows). */
    interface OwnerListingCounts {
        Long getTotal();
        Long getDrafts();
        Long getAvailable();
        Long getRented();
        Long getVerified();
    }

    @Query("SELECT COUNT(p) AS total, "
            + "SUM(CASE WHEN p.status = 'DRAFT' THEN 1L ELSE 0L END) AS drafts, "
            + "SUM(CASE WHEN p.status = 'PUBLISHED' AND p.available = true THEN 1L ELSE 0L END) AS available, "
            + "SUM(CASE WHEN p.available = false THEN 1L ELSE 0L END) AS rented, "
            + "SUM(CASE WHEN p.verified = true THEN 1L ELSE 0L END) AS verified "
            + "FROM Property p WHERE p.owner = :owner")
    OwnerListingCounts countOwnerListings(@Param("owner") User owner);

    Optional<Property> findByEndorsementToken(String endorsementToken);

    /** Backs the saved-properties list, which is also mapped through
     *  PropertyResponseDto -- same owner fetch-join reasoning as
     *  findByOwnerOrderByIdDesc above. */
    @EntityGraph(attributePaths = "owner")
    List<Property> findByIdIn(Collection<Long> ids);

    /** Listings due for the 7-day "still available?" reminder -- no
     *  reminder sent yet, and it's been long enough since last confirmed. */
    List<Property> findByAvailableTrueAndRemindedAtIsNullAndLastConfirmedAtBefore(LocalDateTime threshold);

    /** Listings whose reminder grace period has expired with no response. */
    List<Property> findByAvailableTrueAndRemindedAtIsNotNullAndRemindedAtBefore(LocalDateTime threshold);

    List<Property> findByPhotoApprovedFalseAndPhotoRejectedFalse();

    /** Backs the unpaginated getAllProperties() feed, mapped through
     *  PropertyResponseDto for every row -- same owner fetch-join
     *  reasoning as findByOwnerOrderByIdDesc. */
    @EntityGraph(attributePaths = "owner")
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
    // owner is fetch-joined below (LEFT, since a listing can have a null
    // owner in legacy data) so this list query -- mapped through
    // PropertyResponseDto per row -- costs one query, not one plus one
    // owner SELECT per property. A to-one fetch join is safe to combine
    // with Pageable, unlike a fetch join on imageUrls/customAmenities/
    // etc., which would force in-memory pagination -- those use
    // @BatchSize on the entity instead.
    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.owner WHERE p.status = 'PUBLISHED' ORDER BY p.id DESC")
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
    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.owner WHERE p.status = 'PUBLISHED' " +
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
    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.owner WHERE " +
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

    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.owner WHERE " +
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
     *
     * No free-text `q` params here on purpose: PropertyService#filter
     * always calls the token-bearing overload below (with all six
     * tokens null when `q` is blank, which the WHERE clause already
     * treats as "no constraint"), so this overload's only caller today
     * is a direct repository test. Kept as its own method rather than
     * folded away because a caller that only has the ten typed filters
     * and no search box -- a future admin endpoint, say -- shouldn't
     * have to invent six nulls to get a plain paginated filter.
     */
    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.owner WHERE " +
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
     * Free-text search doc (applies to both `q`-bearing queries below).
     *
     * `q` is pre-tokenized server-side (PropertyService#tokenizeSearchQuery)
     * into up to 6 lowercase words, padded with nulls -- a
     * JPQL query is fixed at compile time, so a variable-arity "AND every
     * word matches somewhere" can't be expressed directly the way the
     * houseType/minPrice/etc. null-checks are. Each token independently
     * requires a match in title, buildingName, location, description,
     * houseType, nearbyFacilities, or customAmenities (any one field is
     * enough per token; different tokens can match different fields).
     * A null token (the query had fewer than 6 words) always
     * passes, so a short or absent `q` behaves exactly as if these
     * clauses weren't there at all.
     *
     * Deliberately NOT covered: the ~20 boolean amenity columns (pool,
     * gym, ac, ...). Matching "pool" against p.pool = true per token
     * would multiply this clause by every amenity column; amenity-word
     * search still happens client-side (PropertySearch, roost_app) over
     * whatever page was already fetched. Structured filters the app
     * already extracts from the query text (furnished, parking, wifi,
     * water, security) go through the existing typed params above, not
     * `q`.
     */
    @Query("""
          SELECT p FROM Property p LEFT JOIN FETCH p.owner WHERE
          p.status = 'PUBLISHED' AND
          p.available = true AND
          (:houseType IS NULL OR p.houseType = :houseType) AND
          (:minPrice IS NULL OR p.price >= :minPrice) AND
          (:maxPrice IS NULL OR p.price <= :maxPrice) AND
          (:bedrooms IS NULL OR p.bedrooms >= :bedrooms) AND
          (:furnished IS NULL OR p.furnished = :furnished) AND
          (:parking IS NULL OR p.parking = :parking) AND
          (:wifi IS NULL OR p.wifi = :wifi) AND
          (:water IS NULL OR p.water = :water) AND
          (:security IS NULL OR p.security = :security) AND
          (:verified IS NULL OR p.verified = :verified)
          AND (:token1 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token1, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token1, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token1, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token1, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token1, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token1, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token1, '%'))
          ))
          AND (:token2 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token2, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token2, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token2, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token2, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token2, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token2, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token2, '%'))
          ))
          AND (:token3 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token3, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token3, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token3, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token3, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token3, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token3, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token3, '%'))
          ))
          AND (:token4 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token4, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token4, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token4, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token4, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token4, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token4, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token4, '%'))
          ))
          AND (:token5 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token5, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token5, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token5, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token5, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token5, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token5, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token5, '%'))
          ))
          AND (:token6 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token6, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token6, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token6, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token6, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token6, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token6, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token6, '%'))
          ))
          ORDER BY p.id DESC
          """)
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
            @Param("token1") String token1,
            @Param("token2") String token2,
            @Param("token3") String token3,
            @Param("token4") String token4,
            @Param("token5") String token5,
            @Param("token6") String token6,
            Pageable pageable);


    /**
     * Same as the paginated filterProperties above but distance-sorted;
     * see that method's doc for the `q`/token parameters.
     */
    @Query("""
          SELECT p FROM Property p LEFT JOIN FETCH p.owner WHERE
          p.status = 'PUBLISHED' AND
          p.available = true AND
          (:houseType IS NULL OR p.houseType = :houseType) AND
          (:minPrice IS NULL OR p.price >= :minPrice) AND
          (:maxPrice IS NULL OR p.price <= :maxPrice) AND
          (:bedrooms IS NULL OR p.bedrooms >= :bedrooms) AND
          (:furnished IS NULL OR p.furnished = :furnished) AND
          (:parking IS NULL OR p.parking = :parking) AND
          (:wifi IS NULL OR p.wifi = :wifi) AND
          (:water IS NULL OR p.water = :water) AND
          (:security IS NULL OR p.security = :security) AND
          (:verified IS NULL OR p.verified = :verified) AND
          p.latitude IS NOT NULL AND p.longitude IS NOT NULL
          AND (:token1 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token1, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token1, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token1, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token1, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token1, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token1, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token1, '%'))
          ))
          AND (:token2 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token2, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token2, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token2, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token2, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token2, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token2, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token2, '%'))
          ))
          AND (:token3 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token3, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token3, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token3, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token3, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token3, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token3, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token3, '%'))
          ))
          AND (:token4 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token4, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token4, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token4, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token4, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token4, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token4, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token4, '%'))
          ))
          AND (:token5 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token5, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token5, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token5, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token5, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token5, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token5, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token5, '%'))
          ))
          AND (:token6 IS NULL OR (
              LOWER(p.title) LIKE CONCAT('%', :token6, '%') OR
              LOWER(COALESCE(p.buildingName, '')) LIKE CONCAT('%', :token6, '%') OR
              LOWER(p.location) LIKE CONCAT('%', :token6, '%') OR
              LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :token6, '%') OR
              LOWER(COALESCE(p.houseType, '')) LIKE CONCAT('%', :token6, '%') OR
              LOWER(COALESCE(p.nearbyFacilities, '')) LIKE CONCAT('%', :token6, '%') OR
              EXISTS (SELECT ca FROM Property p2 JOIN p2.customAmenities ca WHERE p2.id = p.id AND LOWER(ca) LIKE CONCAT('%', :token6, '%'))
          ))
          ORDER BY (6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) *
          cos(radians(p.longitude) - radians(:lng)) +
          sin(radians(:lat)) * sin(radians(p.latitude)))) ASC
          """)
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
            @Param("token1") String token1,
            @Param("token2") String token2,
            @Param("token3") String token3,
            @Param("token4") String token4,
            @Param("token5") String token5,
            @Param("token6") String token6,
            @Param("lat") double lat,
            @Param("lng") double lng,
            Pageable pageable);

    /** Just what the nearby-facilities backfill needs, so it never loads whole entities. */
    interface GeoPoint {
        Long getId();

        Double getLatitude();

        Double getLongitude();
    }

    /**
     * GPS-verified listings that have never had a facilities lookup
     * (nearbyFacilities IS NULL). A completed lookup that found nothing is
     * stored as "[]", so it is NOT returned here and isn't re-queried;
     * a failed lookup stores nothing, so it is.
     */
    @Query("select p.id as id, p.latitude as latitude, p.longitude as longitude "
            + "from Property p "
            + "where p.gpsVerified = true and p.nearbyFacilities is null "
            + "and p.latitude is not null and p.longitude is not null "
            + "order by p.id")
    List<GeoPoint> findGpsVerifiedMissingNearbyFacilities(Pageable pageable);

    /**
     * Writes only the facilities column. A targeted UPDATE (rather than
     * load + save) so a lookup that took seconds can't overwrite fields the
     * landlord edited in the meantime.
     */
    @Transactional
    @Modifying
    @Query("update Property p set p.nearbyFacilities = :json where p.id = :id")
    int updateNearbyFacilities(@Param("id") Long id, @Param("json") String json);
}
