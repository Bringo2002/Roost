package com.roost.repository;

import com.roost.model.VerificationAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerificationAuditEventRepository extends JpaRepository<VerificationAuditEvent, Long> {

    List<VerificationAuditEvent> findByDocumentIdOrderByCreatedAtAscIdAsc(Long documentId);

    List<VerificationAuditEvent> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);
}
