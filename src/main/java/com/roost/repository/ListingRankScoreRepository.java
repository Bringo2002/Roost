package com.roost.repository;

import com.roost.model.ListingRankScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@link ListingRankScore}, keyed by property id. The
 * ranking job loads existing rows with the inherited {@code findAllById}
 * and writes back with {@code saveAll}, so no custom queries are needed.
 */
@Repository
public interface ListingRankScoreRepository extends JpaRepository<ListingRankScore, Long> {
}
