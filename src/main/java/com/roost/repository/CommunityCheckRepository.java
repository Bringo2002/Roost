package com.roost.repository;

import com.roost.model.CommunityCheck;
import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityCheckRepository extends JpaRepository<CommunityCheck, Long> {

    /** One confirmation per tenant per listing. */
    boolean existsByPropertyAndRespondent(Property property, User respondent);

    /** Counts confirmations where the visitor says the listing was
     *  accurate across the board -- the signal that actually drives the
     *  Community Verified badge, as opposed to raw response volume. */
    @Query("SELECT COUNT(c) FROM CommunityCheck c WHERE c.property = :property " +
           "AND c.visited = true AND c.photosAccurate = true " +
           "AND c.locationAccurate = true AND c.priceAccurate = true")
    long countFullyAccurateConfirmations(@Param("property") Property property);
}
