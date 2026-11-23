package com.roost.repository;

import com.roost.model.ChatVisibility;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatVisibilityRepository extends JpaRepository<ChatVisibility, Long> {
    Optional<ChatVisibility> findByUserAndPartner(User user, User partner);

    /** Deletes every visibility row where the user is either side of the pair.
     *  Returns how many rows were removed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ChatVisibility c WHERE c.user = :user OR c.partner = :user")
    int deleteAllInvolving(@Param("user") User user);
}
