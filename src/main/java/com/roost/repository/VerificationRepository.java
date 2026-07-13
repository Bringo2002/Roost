package com.roost.repository;

import com.roost.model.VerificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.roost.model.User;
import java.util.List;

@Repository
public interface VerificationRepository extends JpaRepository<VerificationRecord, Long> {
    List<VerificationRecord> findByTenant(User tenant);
    List<VerificationRecord> findByPropertyId(Long propertyId);
}
