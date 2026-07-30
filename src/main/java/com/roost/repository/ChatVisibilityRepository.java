package com.roost.repository;

import com.roost.model.ChatVisibility;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatVisibilityRepository extends JpaRepository<ChatVisibility, Long> {
    Optional<ChatVisibility> findByUserAndPartner(User user, User partner);
}
