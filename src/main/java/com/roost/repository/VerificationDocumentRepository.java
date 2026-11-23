package com.roost.repository;

import com.roost.model.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, Long> {

    List<VerificationDocument> findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(Long ownerId);

    /** Every document the user uploaded, including ones already marked deleted.
     *  Account erasure uses it so no file of theirs is left behind. */
    List<VerificationDocument> findByOwnerId(Long ownerId);

    List<VerificationDocument> findByClaimIdAndDeletedAtIsNull(Long claimId);

    /** Files whose review was decided before the cutoff and that are still stored. */
    @Query("SELECT d FROM VerificationDocument d WHERE d.deletedAt IS NULL AND d.reviewedAt < :cutoff")
    List<VerificationDocument> findStoredReviewedBefore(@Param("cutoff") LocalDateTime cutoff);
}
