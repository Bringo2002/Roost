package com.roost.repository;

import com.roost.model.ChatVisibility;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatVisibilityRepository extends JpaRepository<ChatVisibility, Long> {
    Optional<ChatVisibility> findByUserAndPartner(User user, User partner);

    /**
     * Batch: every conversation this user has deleted (hidden from their list),
     * in one query. Returns [partnerId, hiddenSinceMessageId]. Rows that only
     * "cleared" a chat (no hidden cutoff) are not included.
     */
    @Query("SELECT c.partner.id, c.hiddenSinceMessageId FROM ChatVisibility c " +
           "WHERE c.user = :user AND c.hiddenSinceMessageId IS NOT NULL")
    List<Object[]> findHiddenChats(@Param("user") User user);

    /** Deletes every visibility row where the user is either side of the pair.
     *  Returns how many rows were removed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ChatVisibility c WHERE c.user = :user OR c.partner = :user")
    int deleteAllInvolving(@Param("user") User user);
}
