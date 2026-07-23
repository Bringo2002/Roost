package com.roost.repository;

import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByLocationContainingIgnoreCase(String location);

    List<Property> findByAvailableTrue();

    List<Property> findByType(String type);

    List<Property> findByPriceBetween(double minPrice, double maxPrice);

    List<Property> findByOwner(User owner);

    List<Property> findByIdIn(Collection<Long> ids);

    List<Property> findByAvailableTrueAndLastConfirmedAtBefore(LocalDateTime threshold);

    @Query("SELECT p FROM Property p WHERE " +
           "p.available = true AND " +
           "p.latitude IS NOT NULL AND p.longitude IS NOT NULL AND " +
           "(6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) < :radiusKm " +
           "ORDER BY (6371 * acos(cos(radians(:lat)) * cos(radians(p.latitude)) * " +
           "cos(radians(p.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(p.latitude)))) ASC")
    List<Property> findNearby(@Param("lat") double lat,
                               @Param("lng") double lng,
                               @Param("radiusKm") double radiusKm);

    @Query("SELECT p FROM Property p WHERE " +
           "p.available = true AND " +
           "(:houseType IS NULL OR LOWER(p.houseType) = LOWER(:houseType)) AND " +
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
}
