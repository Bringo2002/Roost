package com.roost.repository;

import com.roost.model.Message;
import com.roost.model.MessageReaction;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    List<MessageReaction> findByMessage(Message message);
    Optional<MessageReaction> findByMessageAndUserAndEmoji(Message message, User user, String emoji);
    void deleteByMessageAndUserAndEmoji(Message message, User user, String emoji);
}
