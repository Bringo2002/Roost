package com.roost.repository;

import com.roost.model.AuthorityClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AuthorityClaimRepository extends JpaRepository<AuthorityClaim, Long> {

    List<AuthorityClaim> findByPropertyIdAndStatusIn(Long propertyId, Collection<AuthorityClaim.Status> statuses);

    List<AuthorityClaim> findByClaimantIdOrderBySubmittedAtDesc(Long claimantId);
}
