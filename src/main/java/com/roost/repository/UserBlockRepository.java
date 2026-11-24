package com.roost.repository;

import com.roost.model.User;
import com.roost.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    /** True if {@code blocker} has blocked {@code blocked} (this direction only). */
    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    /** True if either of the two users has blocked the other. */
    @Query("SELECT COUNT(b) > 0 FROM UserBlock b WHERE (b.blocker = :a AND b.blocked = :b) "
            + "OR (b.blocker = :b AND b.blocked = :a)")
    boolean existsBetween(@Param("a") User a, @Param("b") User b);

    /** The ids of everyone {@code blocker} has blocked, in one query. */
    @Query("SELECT b.blocked.id FROM UserBlock b WHERE b.blocker = :blocker")
    List<Long> findBlockedIdsByBlocker(@Param("blocker") User blocker);

    /** Everyone {@code blocker} has blocked, newest block first, with the blocked users loaded. */
    @Query("SELECT b FROM UserBlock b JOIN FETCH b.blocked WHERE b.blocker = :blocker ORDER BY b.createdAt DESC")
    List<UserBlock> findWithBlockedByBlocker(@Param("blocker") User blocker);

    /** Removes the block from {@code blocker} to {@code blocked}. Returns how many rows were removed (0 or 1). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserBlock b WHERE b.blocker = :blocker AND b.blocked = :blocked")
    int deleteByPair(@Param("blocker") User blocker, @Param("blocked") User blocked);
}
