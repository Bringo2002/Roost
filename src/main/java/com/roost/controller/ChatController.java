package com.roost.controller;

import com.roost.dto.ConversationSummaryDto;
import com.roost.model.Message;
import com.roost.model.MessageReaction;
import com.roost.model.User;
import com.roost.repository.MessageReactionRepository;
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

    @Autowired
    private MessageReactionRepository reactionRepository;

    /** Base64 attachment payload cap (~5MB raw file after base64 overhead). */
    private static final int MAX_ATTACHMENT_BASE64_CHARS = 7_000_000;

    private static final java.util.concurrent.ConcurrentHashMap<String, LocalDateTime> typingTimestamps = new java.util.concurrent.ConcurrentHashMap<>();

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

        Object replyToIdObj = payload.get("replyToMessageId");
        if (replyToIdObj != null) {
            try {
                message.setReplyToMessageId(Long.valueOf(replyToIdObj.toString()));
            } catch (NumberFormatException ignored) {}
        }

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

    @PutMapping("/{messageId}")
    public ResponseEntity<?> editMessage(@AuthenticationPrincipal User user, @PathVariable Long messageId, @RequestBody Map<String, Object> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return ResponseEntity.notFound().build();
        if (!message.getSender().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only edit your own messages"));
        }
        String content = stringOrNull(payload.get("content"));
        String nonce = stringOrNull(payload.get("nonce"));
        if (content == null || content.isBlank() || nonce == null || nonce.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Edited content and nonce are required"));
        }
        message.setContent(content);
        message.setNonce(nonce);
        message.setEdited(true);
        message.setEditedAt(LocalDateTime.now());
        return ResponseEntity.ok(messageRepository.save(message));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(@AuthenticationPrincipal User user, @PathVariable Long messageId) {
        if (user == null) return ResponseEntity.status(401).build();
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return ResponseEntity.notFound().build();
        if (!message.getSender().getId().equals(user.getId()) && !message.getRecipient().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only delete messages in your conversations"));
        }
        if (message.hasAttachment()) {
            try { r2StorageService.delete(message.getAttachmentStorageKey()); } catch (Exception ignored) {}
        }
        messageRepository.delete(message);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummaryDto>> getConversations(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        List<User> partners = messageRepository.findActiveChatPartners(user);
        List<ConversationSummaryDto> summaries = new java.util.ArrayList<>();
        for (User partner : partners) {
            Message lastMsg = messageRepository.findLastMessage(user, partner);
            Long unread = messageRepository.countUnreadFromUser(partner, user);
            ConversationSummaryDto dto = new ConversationSummaryDto();
            dto.setPartner(partner);
            dto.setUnreadCount(unread != null ? unread : 0);
            if (lastMsg != null) {
                dto.setLastMessageContent(lastMsg.getContent());
                dto.setLastMessageNonce(lastMsg.getNonce());
                dto.setLastMessageTimestamp(lastMsg.getTimestamp());
                dto.setLastMessageSenderId(lastMsg.getSender().getId());
                dto.setLastMessageAttachmentMeta(lastMsg.getAttachmentMeta());
                dto.setLastMessageAttachmentMetaNonce(lastMsg.getAttachmentMetaNonce());
                dto.setHasAttachment(lastMsg.hasAttachment());
            }
            summaries.add(dto);
        }
        summaries.sort((a, b) -> {
            if (a.getLastMessageTimestamp() == null && b.getLastMessageTimestamp() == null) return 0;
            if (a.getLastMessageTimestamp() == null) return 1;
            if (b.getLastMessageTimestamp() == null) return -1;
            return b.getLastMessageTimestamp().compareTo(a.getLastMessageTimestamp());
        });
        return ResponseEntity.ok(summaries);
    }

    @PostMapping("/react/{messageId}")
    public ResponseEntity<?> toggleReaction(@AuthenticationPrincipal User user, @PathVariable Long messageId, @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return ResponseEntity.notFound().build();
        if (!message.getSender().getId().equals(user.getId()) && !message.getRecipient().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a participant"));
        }
        String emoji = payload.get("emoji");
        if (emoji == null || emoji.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "emoji required"));
        var existing = reactionRepository.findByMessageAndUserAndEmoji(message, user, emoji);
        if (existing.isPresent()) {
            reactionRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("action", "removed"));
        }
        MessageReaction reaction = new MessageReaction();
        reaction.setMessage(message);
        reaction.setUser(user);
        reaction.setEmoji(emoji);
        reactionRepository.save(reaction);
        return ResponseEntity.ok(Map.of("action", "added"));
    }

    @PostMapping("/typing/{recipientId}")
    public ResponseEntity<?> sendTypingIndicator(@AuthenticationPrincipal User user, @PathVariable Long recipientId) {
        if (user == null) return ResponseEntity.status(401).build();
        typingTimestamps.put(user.getId() + "->" + recipientId, LocalDateTime.now());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/typing-status/{partnerId}")
    public ResponseEntity<?> getTypingStatus(@AuthenticationPrincipal User user, @PathVariable Long partnerId) {
        if (user == null) return ResponseEntity.status(401).build();
        String key = partnerId + "->" + user.getId();
        LocalDateTime lastTyped = typingTimestamps.get(key);
        boolean isTyping = lastTyped != null && lastTyped.isAfter(LocalDateTime.now().minusSeconds(4));
        return ResponseEntity.ok(Map.of("typing", isTyping));
    }
}
