package com.roost.repository;

import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /** Finds users whose grace period has expired and are still enabled. */
    List<User> findByDeletionScheduledAtBeforeAndEnabledTrue(LocalDateTime cutoff);
}
