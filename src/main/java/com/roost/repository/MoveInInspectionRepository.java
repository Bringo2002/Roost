package com.roost.repository;

import com.roost.model.MoveInInspection;
import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MoveInInspectionRepository extends JpaRepository<MoveInInspection, Long> {

    List<MoveInInspection> findByTenantOrderByCreatedAtDesc(User tenant);

    List<MoveInInspection> findByPropertyOrderByCreatedAtDesc(Property property);

    /** Ownership-checked single fetch -- a tenant can only ever load
     *  their own inspection, never one belonging to someone else who
     *  happens to guess a valid id. */
    Optional<MoveInInspection> findByIdAndTenant(Long id, User tenant);
}
