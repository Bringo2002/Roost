package com.roost.service;

import com.roost.dto.ConversationSummaryDto;
import com.roost.exception.ApiException;
import com.roost.model.Message;
import com.roost.model.MessageReaction;
import com.roost.model.User;
import com.roost.repository.MessageReactionRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class ChatService {

    /** Base64 attachment payload cap (~5MB raw file after base64 overhead). */
    private static final int MAX_ATTACHMENT_BASE64_CHARS = 7_000_000;
    private static final int TYPING_INDICATOR_TTL_SECONDS = 4;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageReactionRepository reactionRepository;
    private final R2StorageService r2StorageService;
    private final FirebasePushService firebasePushService;

    /** In-memory, best-effort presence signal -- deliberately not persisted. */
    private final ConcurrentHashMap<String, LocalDateTime> typingTimestamps = new ConcurrentHashMap<>();

    public ChatService(MessageRepository messageRepository,
                        UserRepository userRepository,
                        MessageReactionRepository reactionRepository,
                        R2StorageService r2StorageService,
                        FirebasePushService firebasePushService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.reactionRepository = reactionRepository;
        this.r2StorageService = r2StorageService;
        this.firebasePushService = firebasePushService;
    }

    public Message sendMessage(User sender, Map<String, Object> payload) {
        Long recipientId = parseRecipientId(payload.get("recipientId"));

        String content = stringOrNull(payload.get("content"));
        String nonce = stringOrNull(payload.get("nonce"));
        String attachmentData = stringOrNull(payload.get("attachmentData"));
        String attachmentNonce = stringOrNull(payload.get("attachmentNonce"));
        String attachmentMeta = stringOrNull(payload.get("attachmentMeta"));
        String attachmentMetaNonce = stringOrNull(payload.get("attachmentMetaNonce"));

        boolean hasContent = isPresent(content) && isPresent(nonce);
        boolean hasAttachment = isPresent(attachmentData) && isPresent(attachmentNonce)
                && isPresent(attachmentMeta) && isPresent(attachmentMetaNonce);

        if (!hasContent && !hasAttachment) {
            throw ApiException.badRequest("A message needs encrypted content, an attachment, or both");
        }
        if (hasAttachment && attachmentData.length() > MAX_ATTACHMENT_BASE64_CHARS) {
            throw ApiException.badRequest("Attachment is too large");
        }
        if (sender.getId().equals(recipientId)) {
            throw ApiException.badRequest("Cannot message yourself");
        }

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> ApiException.badRequest("Recipient not found"));
        if (recipient.getPublicKey() == null || recipient.getPublicKey().isBlank()) {
            throw ApiException.badRequest("Recipient hasn't enabled secure messaging yet");
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
            attachMessageFile(message, attachmentData, attachmentNonce, attachmentMeta, attachmentMetaNonce);
        }
        message.setTimestamp(LocalDateTime.now());
        message.setReplyToMessageId(parseReplyToId(payload.get("replyToMessageId")));

        Message savedMessage = messageRepository.save(message);
        if (hasAttachment) {
            // Not persisted (attachmentData is @Transient) -- populate it
            // on the response so the caller doesn't need a second round
            // trip to read back what it just sent.
            savedMessage.setAttachmentData(attachmentData);
        }

        // Notify the recipient's device. The body is intentionally generic
        // -- messages are E2EE, so the server never has the keys to read
        // content and must not attempt to summarize it in a push payload.
        firebasePushService.sendToUser(
                recipient,
                sender.getName() != null && !sender.getName().isBlank() ? sender.getName() : "New message",
                hasAttachment ? "Sent you an attachment" : "Sent you a message",
                Map.of("type", "chat", "senderId", sender.getId().toString())
        );

        return savedMessage;
    }

    public List<Message> getChatHistory(User user, Long otherUserId) {
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> ApiException.badRequest("User not found"));

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
        return history;
    }

    public List<User> getActiveChats(User user) {
        return messageRepository.findActiveChatPartners(user);
    }

    public long getUnreadCount(User user) {
        Long count = messageRepository.countUnreadMessages(user);
        return count != null ? count : 0;
    }

    public int markAsRead(User user, Long senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> ApiException.badRequest("User not found"));

        List<Message> unread = messageRepository.findUnreadFromUser(sender, user);
        for (Message m : unread) {
            m.setRead(true);
        }
        messageRepository.saveAll(unread);
        return unread.size();
    }

    public Message editMessage(User user, Long messageId, String content, String nonce) {
        Message message = getMessageOrThrow(messageId);
        if (!message.getSender().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only edit your own messages");
        }
        if (!isPresent(content) || !isPresent(nonce)) {
            throw ApiException.badRequest("Edited content and nonce are required");
        }
        message.setContent(content);
        message.setNonce(nonce);
        message.setEdited(true);
        message.setEditedAt(LocalDateTime.now());
        return messageRepository.save(message);
    }

    public void deleteMessage(User user, Long messageId) {
        Message message = getMessageOrThrow(messageId);
        requireParticipant(message, user, "You can only delete messages in your conversations");

        if (message.hasAttachment()) {
            try {
                r2StorageService.delete(message.getAttachmentStorageKey());
            } catch (Exception ignored) {
                // Orphaned blob in storage is preferable to blocking the delete.
            }
        }
        messageRepository.delete(message);
    }

    public List<ConversationSummaryDto> getConversations(User user) {
        List<User> partners = messageRepository.findActiveChatPartners(user);
        List<ConversationSummaryDto> summaries = new ArrayList<>();
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
            LocalDateTime ta = a.getLastMessageTimestamp();
            LocalDateTime tb = b.getLastMessageTimestamp();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return summaries;
    }

    public String toggleReaction(User user, Long messageId, String emoji) {
        Message message = getMessageOrThrow(messageId);
        requireParticipant(message, user, "Not a participant");
        if (!isPresent(emoji)) {
            throw ApiException.badRequest("emoji required");
        }

        Optional<MessageReaction> existing = reactionRepository.findByMessageAndUserAndEmoji(message, user, emoji);
        if (existing.isPresent()) {
            reactionRepository.delete(existing.get());
            return "removed";
        }
        MessageReaction reaction = new MessageReaction();
        reaction.setMessage(message);
        reaction.setUser(user);
        reaction.setEmoji(emoji);
        reactionRepository.save(reaction);
        return "added";
    }

    public void recordTyping(Long userId, Long recipientId) {
        typingTimestamps.put(userId + "->" + recipientId, LocalDateTime.now());
    }

    public boolean isTyping(Long partnerId, Long userId) {
        LocalDateTime lastTyped = typingTimestamps.get(partnerId + "->" + userId);
        return lastTyped != null && lastTyped.isAfter(LocalDateTime.now().minusSeconds(TYPING_INDICATOR_TTL_SECONDS));
    }

    // -- helpers --------------------------------------------------------

    private void attachMessageFile(Message message, String attachmentData, String attachmentNonce,
                                    String attachmentMeta, String attachmentMetaNonce) {
        byte[] rawBytes;
        try {
            rawBytes = Base64.getDecoder().decode(attachmentData);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Attachment data is not valid base64");
        }

        String storageKey;
        try {
            storageKey = r2StorageService.upload(rawBytes);
        } catch (IllegalStateException e) {
            throw ApiException.serviceUnavailable(e.getMessage());
        }

        message.setAttachmentStorageKey(storageKey);
        message.setAttachmentNonce(attachmentNonce);
        message.setAttachmentMeta(attachmentMeta);
        message.setAttachmentMetaNonce(attachmentMetaNonce);
    }

    private Message getMessageOrThrow(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> ApiException.notFound("Message not found"));
    }

    private void requireParticipant(Message message, User user, String errorMessage) {
        boolean isParticipant = message.getSender().getId().equals(user.getId())
                || message.getRecipient().getId().equals(user.getId());
        if (!isParticipant) {
            throw ApiException.forbidden(errorMessage);
        }
    }

    private Long parseRecipientId(Object rawId) {
        try {
            return Long.valueOf(rawId.toString());
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid recipientId");
        }
    }

    private Long parseReplyToId(Object rawId) {
        if (rawId == null) return null;
        try {
            return Long.valueOf(rawId.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String stringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }
}
