package com.roost.repository;

import com.roost.model.MoveInInspection;
import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MoveInInspectionRepository extends JpaRepository<MoveInInspection, Long> {

    // Both property and tenant are fetch-joined because
    // MoveInInspectionResponseDto reads property.getTitle() and
    // tenant.getName() for every row -- whichever side isn't the query
    // parameter varies per row (different tenants inspected the same
    // property over time; the same tenant may have inspected more than
    // one property), so without this each list endpoint would N+1 on
    // the other association. Same shape as the owner fetch-join on
    // PropertyRepository.
    @EntityGraph(attributePaths = {"property", "tenant"})
    List<MoveInInspection> findByTenantOrderByCreatedAtDesc(User tenant);

    @EntityGraph(attributePaths = {"property", "tenant"})
    List<MoveInInspection> findByPropertyOrderByCreatedAtDesc(Property property);

    /** Ownership-checked single fetch -- a tenant can only ever load
     *  their own inspection, never one belonging to someone else who
     *  happens to guess a valid id. */
    Optional<MoveInInspection> findByIdAndTenant(Long id, User tenant);

    /**
     * Inspections past their retention period: those completed before
     * {@code cutoff}, or, for one that was never completed, created before it.
     * The nightly retention job deletes them.
     */
    @Query("SELECT i FROM MoveInInspection i WHERE COALESCE(i.completedAt, i.createdAt) < :cutoff")
    List<MoveInInspection> findExpired(@Param("cutoff") LocalDateTime cutoff);
}
