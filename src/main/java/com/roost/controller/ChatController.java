package com.roost.controller;

import com.roost.model.Message;
import com.roost.model.User;
import com.roost.repository.MessageRepository;
import com.roost.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> sendMessage(@AuthenticationPrincipal User sender, @RequestBody Map<String, Object> payload) {
        if (sender == null) return ResponseEntity.status(401).build();

        Long recipientId;
        try {
            recipientId = Long.valueOf(payload.get("recipientId").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid recipientId");
        }
        String content = payload.get("content") != null ? payload.get("content").toString() : "";
        if (content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message content cannot be empty"));
        }

        if (sender.getId().equals(recipientId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot message yourself"));
        }

        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient not found"));
        }

        Message message = new Message();
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());

        Message savedMessage = messageRepository.save(message);
        return ResponseEntity.ok(savedMessage);
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Message>> getChatHistory(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        if (user == null) return ResponseEntity.status(401).build();
        User otherUser = userRepository.findById(userId).orElse(null);
        if (otherUser == null) {
            return ResponseEntity.badRequest().build();
        }
        
        List<Message> history = messageRepository.findChatHistory(user, otherUser);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/active")
    public ResponseEntity<List<User>> getActiveChats(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();

        List<User> partners = messageRepository.findActiveChatPartners(user);
        return ResponseEntity.ok(partners);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        Long count = messageRepository.countUnreadMessages(user);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/mark-read/{userId}")
    public ResponseEntity<?> markAsRead(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        if (user == null) return ResponseEntity.status(401).build();
        User sender = userRepository.findById(userId).orElse(null);
        if (sender == null) return ResponseEntity.badRequest().build();

        List<Message> unread = messageRepository.findUnreadFromUser(sender, user);
        for (Message m : unread) {
            m.setRead(true);
        }
        messageRepository.saveAll(unread);
        return ResponseEntity.ok(Map.of("marked", unread.size()));
    }
}
