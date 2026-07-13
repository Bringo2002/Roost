package com.roost.repository;

import com.roost.model.Review;
import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
