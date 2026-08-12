package com.roost.repository;

import com.roost.model.Review;
import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByPropertyOrderByCreatedAtDesc(Property property);

    Optional<Review> findByPropertyAndReviewer(Property property, User reviewer);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.property = :property")
    Double findAverageRatingByProperty(@Param("property") Property property);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.property = :property")
    Long countByProperty(@Param("property") Property property);

    /**
     * One row per property with reviews, in ONE query -- used by
     * PropertyService.populateRatings(List<Property>) instead of the
     * previous per-property findAverageRatingByProperty +
     * countByProperty pair, which ran 2 queries for every property on
     * every list endpoint (40 queries for a single page of 20 listings).
     * Properties with zero reviews simply don't appear in the result;
     * the caller treats a missing id as avg=0/count=0, same as before.
     */
    interface PropertyRatingSummary {
        Long getPropertyId();
        Double getAvgRating();
        Long getReviewCount();
    }

    @Query("SELECT r.property.id AS propertyId, AVG(r.rating) AS avgRating, COUNT(r) AS reviewCount " +
           "FROM Review r WHERE r.property.id IN :propertyIds GROUP BY r.property.id")
    List<PropertyRatingSummary> findRatingSummariesByPropertyIds(@Param("propertyIds") Collection<Long> propertyIds);
}
