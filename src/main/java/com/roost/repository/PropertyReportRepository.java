package com.roost.repository;

import com.roost.model.Property;
import com.roost.model.PropertyReport;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PropertyReportRepository extends JpaRepository<PropertyReport, Long> {

    List<PropertyReport> findByPropertyOrderByCreatedAtDesc(Property property);

    /** One report per user per listing -- prevents a single person
     *  inflating the count by reporting the same listing repeatedly. */
    boolean existsByPropertyAndReportedBy(Property property, User reportedBy);

    long countByProperty(Property property);

    void deleteByProperty(Property property);

    /**
     * Every property with at least one report that's newer than the
     * property's last reportsReviewedAt (or has never been reviewed at
     * all). This is what surfaces a listing in the admin flagged queue
     * -- deliberately independent of REPORT_THRESHOLD in
     * PropertyService, so a single report is visible to an admin for
     * manual judgment even though it isn't enough on its own to
     * auto-hide the listing.
     */
    // owner is fetch-joined because the admin queue maps every result
    // through PropertyResponseDto (which reads .getOwner()) -- without it
    // that's one extra owner SELECT per flagged listing.
    @Query(
        "SELECT DISTINCT p FROM PropertyReport r JOIN r.property p LEFT JOIN FETCH p.owner " +
        "WHERE p.reportsReviewedAt IS NULL OR r.createdAt > p.reportsReviewedAt"
    )
    List<Property> findPropertiesWithUnreviewedReports();

    /**
     * Total report count per property, for many properties in ONE query --
     * used by PropertyService.getFlaggedForReview instead of calling
     * countByProperty once per flagged listing (1 + N queries). Properties
     * with no reports don't appear in the result; the caller treats a
     * missing id as 0.
     */
    interface PropertyReportCount {
        Long getPropertyId();
        Long getReportCount();
    }

    @Query("SELECT r.property.id AS propertyId, COUNT(r) AS reportCount " +
           "FROM PropertyReport r WHERE r.property.id IN :propertyIds GROUP BY r.property.id")
    List<PropertyReportCount> countReportsByPropertyIds(@Param("propertyIds") Collection<Long> propertyIds);
}
