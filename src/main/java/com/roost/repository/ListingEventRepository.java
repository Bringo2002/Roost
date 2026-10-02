package com.roost.repository;

import com.roost.model.ListingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Write-heavy log repository for {@link ListingEvent}. The only read the
 * ranking job needs is a single grouped aggregate over a time window, so
 * it never loads event rows (or their lazy properties) at all.
 */
@Repository
public interface ListingEventRepository extends JpaRepository<ListingEvent, Long> {

    /** Number of events of one type for one listing in the aggregation window. */
    interface EventCount {
        Long getPropertyId();

        String getEventType();

        long getEventCount();
    }

    /**
     * Counts events per (listing, type) since {@code since} in one query,
     * served by {@code idx_listing_events_created}. One statement for the
     * whole catalogue, so ranking never issues a query per listing (N+1).
     */
    @Query("""
            SELECT e.property.id AS propertyId, e.eventType AS eventType, COUNT(e) AS eventCount
            FROM ListingEvent e
            WHERE e.createdAt >= :since
            GROUP BY e.property.id, e.eventType
            """)
    List<EventCount> countByPropertyAndTypeSince(@Param("since") Instant since);
}
