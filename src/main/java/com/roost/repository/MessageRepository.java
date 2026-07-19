package com.roost.repository;

import com.roost.model.Message;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE (m.sender = :user1 AND m.recipient = :user2) OR (m.sender = :user2 AND m.recipient = :user1) ORDER BY m.timestamp ASC")
    List<Message> findChatHistory(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT DISTINCT u FROM User u WHERE u IN (SELECT m.sender FROM Message m WHERE m.recipient = :user) OR u IN (SELECT m.recipient FROM Message m WHERE m.sender = :user)")
    List<User> findActiveChatPartners(@Param("user") User user);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.recipient = :user AND m.read = false")
    Long countUnreadMessages(@Param("user") User user);

    @Query("SELECT m FROM Message m WHERE m.sender = :sender AND m.recipient = :recipient AND m.read = false")
    List<Message> findUnreadFromUser(@Param("sender") User sender, @Param("recipient") User recipient);

    @Query("SELECT m FROM Message m WHERE m.id = (SELECT MAX(m2.id) FROM Message m2 WHERE (m2.sender = :user AND m2.recipient = :partner) OR (m2.sender = :partner AND m2.recipient = :user))")
    Message findLastMessage(@Param("user") User user, @Param("partner") User partner);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.sender = :sender AND m.recipient = :recipient AND m.read = false")
    Long countUnreadFromUser(@Param("sender") User sender, @Param("recipient") User recipient);
}
