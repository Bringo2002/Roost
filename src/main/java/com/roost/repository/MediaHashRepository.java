package com.roost.repository;

import com.roost.model.MediaHash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Read-heavy repository for the content-hash deduplication table.
 * Lookups by {@link #findByContentHash(String)} run on the primary key
 * index, so they are O(1) regardless of table size.
 */
@Repository
public interface MediaHashRepository extends JpaRepository<MediaHash, String> {

    /** Looks up an existing upload by its SHA-256 content hash. */
    Optional<MediaHash> findByContentHash(String contentHash);

    /**
     * Forgets the upload stored under this key. Called once the file itself
     * has been deleted, so a later upload of the same bytes is stored afresh
     * instead of being matched to a file that no longer exists.
     * Returns how many rows were removed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MediaHash m WHERE m.r2Key = :key")
    int deleteByR2Key(@Param("key") String key);
}
