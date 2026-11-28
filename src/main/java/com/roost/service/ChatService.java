package com.roost.service;

import com.roost.dto.ChatUserDto;
import com.roost.dto.ConversationSummaryDto;
import com.roost.dto.SendMessageRequestDto;
import com.roost.exception.ApiException;
import com.roost.model.ChatVisibility;
import com.roost.model.Message;
import com.roost.model.MessageReaction;
import com.roost.model.NotificationType;
import com.roost.model.User;
import com.roost.repository.ChatVisibilityRepository;
import com.roost.repository.MessageReactionRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.UserBlockRepository;
import com.roost.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Base64 attachment payload cap (~5MB raw file after base64 overhead). */
    private static final int MAX_ATTACHMENT_BASE64_CHARS = 7_000_000;
    private static final int TYPING_INDICATOR_TTL_SECONDS = 4;
    /** Messages older than this cannot be edited — matches WhatsApp. */
    private static final int EDIT_WINDOW_MINUTES = 15;

    /**
     * Chat history used to download every message's attachment from R2
     * one at a time, sequentially, all within the same request -- a
     * conversation with several photos/voice notes could easily push
     * total latency past the client's 10s timeout waiting on that loop
     * alone. Downloads now run concurrently on this pool instead, and
     * decryptAttachments caps the overall wait so one slow file can't
     * hold up the whole response (see decryptAttachments). Daemon
     * threads so this pool never blocks JVM shutdown.
     */
    private static final ExecutorService attachmentDownloadExecutor = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "attachment-download");
        thread.setDaemon(true);
        return thread;
    });

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageReactionRepository reactionRepository;
    private final ChatVisibilityRepository chatVisibilityRepository;
    private final UserBlockRepository userBlockRepository;
    private final R2StorageService r2StorageService;
    private final FirebasePushService firebasePushService;

    /** In-memory, best-effort presence signal -- deliberately not persisted. */
    private final ConcurrentHashMap<String, LocalDateTime> typingTimestamps = new ConcurrentHashMap<>();

    public ChatService(MessageRepository messageRepository,
                        UserRepository userRepository,
                        MessageReactionRepository reactionRepository,
                        ChatVisibilityRepository chatVisibilityRepository,
                        UserBlockRepository userBlockRepository,
                        R2StorageService r2StorageService,
                        FirebasePushService firebasePushService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.reactionRepository = reactionRepository;
        this.chatVisibilityRepository = chatVisibilityRepository;
        this.userBlockRepository = userBlockRepository;
        this.r2StorageService = r2StorageService;
        this.firebasePushService = firebasePushService;
    }

    /**
     * Sends an end-to-end-encrypted message from [sender] to the
     * recipient identified by the DTO's recipientId.
     */
    public Message sendMessage(User sender, SendMessageRequestDto dto) {
        Long recipientId = dto.getRecipientId();

        String content = dto.getContent();
        String nonce = dto.getNonce();
        String attachmentData = dto.getAttachmentData();
        String attachmentNonce = dto.getAttachmentNonce();
        String attachmentMeta = dto.getAttachmentMeta();
        String attachmentMetaNonce = dto.getAttachmentMetaNonce();

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

        // Idempotency: if the client retries with the same nonce, return
        // the previously saved message rather than creating a duplicate.
        if (isPresent(nonce)) {
            Optional<Message> existing = messageRepository.findBySenderAndNonce(sender, nonce);
            if (existing.isPresent()) {
                log.info("msg.dedup sender={} nonce={} existingId={}", sender.getId(), nonce, existing.get().getId());
                return existing.get();
            }
        }

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> ApiException.badRequest("Recipient not found"));
        // A deleted (or otherwise disabled) account can't receive messages.
        // Checked before the key check below: deletion also wipes the key,
        // and "hasn't enabled secure messaging" would be the wrong reason.
        if (!recipient.isEnabled()) {
            throw ApiException.badRequest("This account is no longer available");
        }
        // A block in either direction ends the conversation. The blocked
        // sender is told only that they can't message this person, not who
        // blocked whom.
        if (userBlockRepository.existsByBlockerAndBlocked(sender, recipient)) {
            throw ApiException.forbidden("Unblock this person to message them");
        }
        if (userBlockRepository.existsByBlockerAndBlocked(recipient, sender)) {
            throw ApiException.forbidden("You can't send messages to this person");
        }
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
        message.setReplyToMessageId(dto.getReplyToMessageId());

        Message savedMessage = messageRepository.save(message);
        if (hasAttachment) {
            // Not persisted (attachmentData is @Transient) -- populate it
            // on the response so the caller doesn't need a second round
            // trip to read back what it just sent.
            savedMessage.setAttachmentData(attachmentData);
        }

        log.info("msg.sent id={} sender={} recipient={} hasAttachment={}",
                savedMessage.getId(), sender.getId(), recipientId, hasAttachment);

        // Notify the recipient's device. The body is intentionally generic
        // -- messages are E2EE, so the server never has the keys to read
        // content and must not attempt to summarize it in a push payload.
        firebasePushService.sendToUser(
                recipient,
                sender.getName() != null && !sender.getName().isBlank() ? sender.getName() : "New message",
                hasAttachment ? "Sent you an attachment" : "Sent you a message",
                Map.of("type", NotificationType.CHAT.key(), "senderId", sender.getId().toString())
        );

        return savedMessage;
    }

    /** Default page size when the client doesn't specify one. */
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 30;
    /** Hard ceiling regardless of what the client asks for. */
    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    /**
     * Fetches chat history with {@code otherUserId}, decrypting attachments
     * as before. Exactly one of the following applies, in priority order:
     * <ul>
     *   <li>{@code afterId} set -- returns every message newer than it,
     *       oldest first, no limit. Used by the client's polling loop so
     *       each poll only pulls new messages instead of the whole
     *       conversation.</li>
     *   <li>{@code beforeId} set -- returns up to {@code limit} messages
     *       older than it, oldest first. Used for "load earlier messages"
     *       when the user scrolls to the top.</li>
     *   <li>neither set -- returns the most recent {@code limit} messages,
     *       oldest first. Used for the initial load of a conversation.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<Message> getChatHistory(User user, Long otherUserId, Long beforeId, Long afterId, Integer limit) {
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> ApiException.badRequest("User not found"));

        // Someone the user blocked has no visible conversation; it comes
        // back, unchanged, if they are unblocked.
        if (userBlockRepository.existsByBlockerAndBlocked(user, otherUser)) {
            return new ArrayList<>();
        }

        int pageSize = (limit != null && limit > 0) ? Math.min(limit, MAX_HISTORY_PAGE_SIZE) : DEFAULT_HISTORY_PAGE_SIZE;
        Long clearedBeforeId = chatVisibilityRepository.findByUserAndPartner(user, otherUser)
                .map(ChatVisibility::getClearedBeforeMessageId)
                .orElse(null);

        List<Message> history;
        if (afterId != null) {
            history = messageRepository.findChatHistoryAfter(user, otherUser, afterId, clearedBeforeId);
        } else if (beforeId != null) {
            history = messageRepository.findChatHistoryBeforeDesc(user, otherUser, beforeId, clearedBeforeId, PageRequest.of(0, pageSize));
            Collections.reverse(history);
        } else {
            history = messageRepository.findChatHistoryLatestDesc(user, otherUser, clearedBeforeId, PageRequest.of(0, pageSize));
            Collections.reverse(history);
        }

        decryptAttachments(history);
        return history;
    }

    /**
     * Downloads every message's attachment from R2 concurrently instead
     * of one at a time -- for a conversation with several photos or
     * voice notes, sequential downloads could take long enough to blow
     * past the client's request timeout entirely, failing the whole
     * history load over attachments that individually would have been
     * fine. Caps the overall wait at 7s (comfortably under the client's
     * 10s timeout, leaving room for the rest of the request/response
     * cycle) -- any download still in flight when that expires is left
     * null, same graceful degradation as an individual download
     * failure: the client shows "couldn't load attachment" for that
     * one message rather than failing the entire load.
     */
    private void decryptAttachments(List<Message> history) {
        List<CompletableFuture<Void>> downloads = history.stream()
                .filter(Message::hasAttachment)
                .map(message -> CompletableFuture.runAsync(() -> {
                    try {
                        byte[] bytes = r2StorageService.download(message.getAttachmentStorageKey());
                        message.setAttachmentData(Base64.getEncoder().encodeToString(bytes));
                    } catch (Exception e) {
                        log.warn("attachment.download.failed key={} msgId={}",
                                message.getAttachmentStorageKey(), message.getId(), e);
                    }
                }, attachmentDownloadExecutor))
                .toList();

        if (downloads.isEmpty()) return;

        try {
            CompletableFuture.allOf(downloads.toArray(new CompletableFuture[0]))
                    .get(7, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Timed out or one of the futures failed unexpectedly --
            // whatever downloads did finish already wrote their bytes
            // onto their message directly, so we still return those;
            // the rest are simply left null.
        }
    }

    /**
     * "Clear chat": hides every message currently in this conversation
     * from [user]'s view only -- [partner] keeps their full history.
     * Implemented as a cutoff (the latest message id at this moment)
     * rather than deleting rows, since the messages table is shared
     * between both participants.
     */
    public void clearChat(User user, Long partnerId) {
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> ApiException.badRequest("User not found"));

        Message lastMsg = messageRepository.findLastMessage(user, partner);
        if (lastMsg == null) return; // nothing to clear

        ChatVisibility visibility = getOrCreateVisibility(user, partner);
        visibility.setClearedBeforeMessageId(lastMsg.getId());
        chatVisibilityRepository.save(visibility);
    }

    /**
     * "Delete chat": hides this conversation from [user]'s conversation
     * list only. Automatically reappears the moment [partner] sends a
     * new message -- there's no separate "undelete" action, matching
     * WhatsApp/Telegram. Existing messages aren't cleared; if the
     * conversation reappears, the previous history is still there.
     */
    public void deleteChat(User user, Long partnerId) {
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> ApiException.badRequest("User not found"));

        Message lastMsg = messageRepository.findLastMessage(user, partner);
        long hiddenSince = lastMsg != null ? lastMsg.getId() : 0L;

        ChatVisibility visibility = getOrCreateVisibility(user, partner);
        visibility.setHiddenSinceMessageId(hiddenSince);
        chatVisibilityRepository.save(visibility);
    }

    private ChatVisibility getOrCreateVisibility(User user, User partner) {
        return chatVisibilityRepository.findByUserAndPartner(user, partner)
                .orElseGet(() -> {
                    ChatVisibility v = new ChatVisibility();
                    v.setUser(user);
                    v.setPartner(partner);
                    return v;
                });
    }

    /** True if this conversation is currently hidden from [user]'s list
     *  (i.e. deleteChat was called and no newer message has arrived since). */
    private boolean isHidden(User user, User partner, Message lastMsg) {
        return chatVisibilityRepository.findByUserAndPartner(user, partner)
                .map(ChatVisibility::getHiddenSinceMessageId)
                .map(hiddenSince -> lastMsg == null || lastMsg.getId() <= hiddenSince)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<User> getActiveChats(User user) {
        List<User> partners = messageRepository.findActiveChatPartners(user);
        Set<Long> blockedIds = new HashSet<>(userBlockRepository.findBlockedIdsByBlocker(user));
        List<User> filtered = partners.stream()
                .filter(p -> !blockedIds.contains(p.getId()))
                .toList();
        if (filtered.isEmpty()) return new ArrayList<>();

        List<Long> partnerIds = filtered.stream().map(User::getId).toList();
        Map<Long, Message> lastMessages = batchLastMessages(user, partnerIds);

        List<User> visible = new ArrayList<>();
        for (User partner : filtered) {
            Message lastMsg = lastMessages.get(partner.getId());
            if (!isHidden(user, partner, lastMsg)) {
                visible.add(partner);
            }
        }
        return visible;
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        Long count = messageRepository.countUnreadNotBlocked(user);
        return count != null ? count : 0;
    }

    public int markAsRead(User user, Long senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> ApiException.badRequest("User not found"));
        return messageRepository.markAllAsRead(sender, user);
    }

    public Message editMessage(User user, Long messageId, String content, String nonce) {
        Message message = getMessageOrThrow(messageId);
        if (!message.getSender().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only edit your own messages");
        }
        if (!isPresent(content) || !isPresent(nonce)) {
            throw ApiException.badRequest("Edited content and nonce are required");
        }
        if (message.getTimestamp() != null
                && message.getTimestamp().plusMinutes(EDIT_WINDOW_MINUTES).isBefore(LocalDateTime.now())) {
            throw ApiException.forbidden("Edit window has expired");
        }
        message.setContent(content);
        message.setNonce(nonce);
        message.setEdited(true);
        message.setEditedAt(LocalDateTime.now());
        log.info("msg.edited id={} by={}", messageId, user.getId());
        return messageRepository.save(message);
    }

    public void deleteMessage(User user, Long messageId) {
        Message message = getMessageOrThrow(messageId);
        requireParticipant(message, user, "You can only delete messages in your conversations");

        if (message.hasAttachment()) {
            try {
                r2StorageService.delete(message.getAttachmentStorageKey());
            } catch (Exception e) {
                log.warn("attachment.cleanup.failed key={} msgId={}", message.getAttachmentStorageKey(), messageId, e);
            }
        }
        messageRepository.delete(message);
        log.info("msg.deleted id={} by={}", messageId, user.getId());
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> getConversations(User user) {
        List<User> partners = messageRepository.findActiveChatPartners(user);
        Set<Long> blockedIds = new HashSet<>(userBlockRepository.findBlockedIdsByBlocker(user));
        List<User> filtered = partners.stream()
                .filter(p -> !blockedIds.contains(p.getId()))
                .toList();
        if (filtered.isEmpty()) return new ArrayList<>();

        List<Long> partnerIds = filtered.stream().map(User::getId).toList();
        Map<Long, Message> lastMessages = batchLastMessages(user, partnerIds);
        Map<Long, Long> unreadCounts = batchUnreadCounts(partnerIds, user);

        List<ConversationSummaryDto> summaries = new ArrayList<>();
        for (User partner : filtered) {
            Message lastMsg = lastMessages.get(partner.getId());
            if (isHidden(user, partner, lastMsg)) {
                continue;
            }
            long unread = unreadCounts.getOrDefault(partner.getId(), 0L);

            ConversationSummaryDto dto = new ConversationSummaryDto();
            dto.setPartner(ChatUserDto.from(partner));
            dto.setUnreadCount(unread);
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

    /** Evicts stale typing indicators every 60s to prevent unbounded memory growth. */
    @Scheduled(fixedRate = 60_000)
    void evictStaleTypingIndicators() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(TYPING_INDICATOR_TTL_SECONDS);
        typingTimestamps.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
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
            log.error("attachment.upload.failed bytes={}", rawBytes.length, e);
            throw ApiException.serviceUnavailable(e.getMessage());
        }

        message.setAttachmentStorageKey(storageKey);
        message.setAttachmentNonce(attachmentNonce);
        message.setAttachmentMeta(attachmentMeta);
        message.setAttachmentMetaNonce(attachmentMetaNonce);
    }

    /** Batch-loads the last message for each partner in one query, keyed by partner id. */
    private Map<Long, Message> batchLastMessages(User user, List<Long> partnerIds) {
        Map<Long, Message> result = new HashMap<>();
        for (Object[] row : messageRepository.findLastMessagesForPartners(user, partnerIds)) {
            Message msg = (Message) row[1];
            Long partnerId = msg.getSender().getId().equals(user.getId())
                    ? msg.getRecipient().getId()
                    : msg.getSender().getId();
            result.put(partnerId, msg);
        }
        return result;
    }

    /** Batch-loads unread counts from each partner in one query, keyed by sender id. */
    private Map<Long, Long> batchUnreadCounts(List<Long> senderIds, User recipient) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadFromUsers(senderIds, recipient)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
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

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
