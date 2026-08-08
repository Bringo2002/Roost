package com.roost.repository;

import com.roost.model.Property;
import com.roost.model.PropertyReport;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyReportRepository extends JpaRepository<PropertyReport, Long> {

    List<PropertyReport> findByPropertyOrderByCreatedAtDesc(Property property);

    /** One report per user per listing -- prevents a single person
     *  inflating the count by reporting the same listing repeatedly. */
    boolean existsByPropertyAndReportedBy(Property property, User reportedBy);

    long countByProperty(Property property);

    /**
     * Every property with at least one report that's newer than the
     * property's last reportsReviewedAt (or has never been reviewed at
     * all). This is what surfaces a listing in the admin flagged queue
     * -- deliberately independent of REPORT_THRESHOLD in
     * PropertyService, so a single report is visible to an admin for
     * manual judgment even though it isn't enough on its own to
     * auto-hide the listing.
     */
    @Query(
        "SELECT DISTINCT r.property FROM PropertyReport r " +
        "WHERE r.property.reportsReviewedAt IS NULL OR r.createdAt > r.property.reportsReviewedAt"
    )
    List<Property> findPropertiesWithUnreviewedReports();
}
