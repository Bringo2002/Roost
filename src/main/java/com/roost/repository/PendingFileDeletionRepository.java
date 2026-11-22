package com.roost.repository;

import com.roost.model.PendingFileDeletion;
import com.roost.model.PendingFileDeletion.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Queue of stored files waiting to be deleted. The nightly job reads what is
 * {@linkplain #findByDeleteAfterLessThanEqualOrderByDeleteAfterAscIdAsc due},
 * oldest first; the unique index on bucket and key keeps a file from being
 * queued twice.
 */
@Repository
public interface PendingFileDeletionRepository extends JpaRepository<PendingFileDeletion, Long> {

    /** Everything whose delete-after date is on or before {@code now}, oldest first. */
    List<PendingFileDeletion> findByDeleteAfterLessThanEqualOrderByDeleteAfterAscIdAsc(LocalDateTime now);

    /** Whether this file is already queued. */
    boolean existsByBucketAndStorageKey(Bucket bucket, String storageKey);
}
