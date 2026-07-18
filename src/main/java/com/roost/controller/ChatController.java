package com.roost.controller;

import com.roost.model.Message;
import com.roost.model.User;
import com.roost.repository.MessageRepository;
import com.roost.repository.UserRepository;
import com.roost.service.R2StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Base64;
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

    @Autowired
    private R2StorageService r2StorageService;

    /** Base64 attachment payload cap (~5MB raw file after base64 overhead). */
    private static final int MAX_ATTACHMENT_BASE64_CHARS = 7_000_000;

    @PostMapping
    public ResponseEntity<?> sendMessage(@AuthenticationPrincipal User sender, @RequestBody Map<String, Object> payload) {
        if (sender == null) return ResponseEntity.status(401).build();

        Long recipientId;
        try {
            recipientId = Long.valueOf(payload.get("recipientId").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid recipientId");
        }

        String content = stringOrNull(payload.get("content"));
        String nonce = stringOrNull(payload.get("nonce"));
        String attachmentData = stringOrNull(payload.get("attachmentData"));
        String attachmentNonce = stringOrNull(payload.get("attachmentNonce"));
        String attachmentMeta = stringOrNull(payload.get("attachmentMeta"));
        String attachmentMetaNonce = stringOrNull(payload.get("attachmentMetaNonce"));

        boolean hasContent = content != null && !content.isBlank() && nonce != null && !nonce.isBlank();
        boolean hasAttachment = attachmentData != null && !attachmentData.isBlank()
                && attachmentNonce != null && !attachmentNonce.isBlank()
                && attachmentMeta != null && !attachmentMeta.isBlank()
                && attachmentMetaNonce != null && !attachmentMetaNonce.isBlank();

        if (!hasContent && !hasAttachment) {
            return ResponseEntity.badRequest().body(Map.of("error", "A message needs encrypted content, an attachment, or both"));
        }
        if (hasAttachment && attachmentData.length() > MAX_ATTACHMENT_BASE64_CHARS) {
            return ResponseEntity.badRequest().body(Map.of("error", "Attachment is too large"));
        }

        if (sender.getId().equals(recipientId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot message yourself"));
        }

        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient not found"));
        }
        if (recipient.getPublicKey() == null || recipient.getPublicKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient hasn't enabled secure messaging yet"));
        }

        Message message = new Message();
        message.setSender(sender);
        message.setRecipient(recipient);
        // content still has a NOT NULL constraint at the database level --
        // Hibernate's ddl-auto=update won't retroactively drop a
        // constraint on an existing column just because the entity
        // annotation changed, so a captionless attachment message must
        // store "" rather than null here to avoid failing the insert.
        message.setContent(hasContent ? content : "");
        if (hasContent) {
            message.setNonce(nonce);
        }
        if (hasAttachment) {
            byte[] rawBytes;
            try {
                rawBytes = Base64.getDecoder().decode(attachmentData);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Attachment data is not valid base64"));
            }

            String storageKey;
            try {
                storageKey = r2StorageService.upload(rawBytes);
            } catch (IllegalStateException e) {
                return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
            }

            message.setAttachmentStorageKey(storageKey);
            message.setAttachmentNonce(attachmentNonce);
            message.setAttachmentMeta(attachmentMeta);
            message.setAttachmentMetaNonce(attachmentMetaNonce);
        }
        message.setTimestamp(LocalDateTime.now());

        Message savedMessage = messageRepository.save(message);
        if (hasAttachment) {
            // Not persisted (attachmentData is @Transient) -- populate it
            // on the response so the caller doesn't need a second round
            // trip to read back what it just sent.
            savedMessage.setAttachmentData(attachmentData);
        }
        return ResponseEntity.ok(savedMessage);
    }

    private static String stringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Message>> getChatHistory(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        if (user == null) return ResponseEntity.status(401).build();
        User otherUser = userRepository.findById(userId).orElse(null);
        if (otherUser == null) {
            return ResponseEntity.badRequest().build();
        }
        
        List<Message> history = messageRepository.findChatHistory(user, otherUser);
        for (Message message : history) {
            if (message.hasAttachment()) {
                try {
                    byte[] bytes = r2StorageService.download(message.getAttachmentStorageKey());
                    message.setAttachmentData(Base64.getEncoder().encodeToString(bytes));
                } catch (Exception e) {
                    // Leave attachmentData null -- the client shows a
                    // "couldn't load attachment" state rather than the
                    // whole history request failing over one bad file.
                }
            }
        }
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
