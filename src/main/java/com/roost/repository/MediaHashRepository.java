package com.roost.repository;

import com.roost.model.MediaHash;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
