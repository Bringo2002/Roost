package com.roost.service;

import com.roost.dto.SendMessageRequestDto;
import com.roost.exception.ApiException;
import com.roost.model.ChatVisibility;
import com.roost.model.Message;
import com.roost.model.MessageReaction;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.ChatVisibilityRepository;
import com.roost.repository.MessageReactionRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.UserBlockRepository;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for {@link ChatService}, covering sendMessage,
 * getChatHistory, editMessage, deleteMessage, toggleReaction, clearChat,
 * deleteChat, markAsRead, getUnreadCount, and typing indicators.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageReactionRepository reactionRepository;
    @Mock private ChatVisibilityRepository chatVisibilityRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private R2StorageService r2StorageService;
    @Mock private FirebasePushService firebasePushService;

    @InjectMocks
    private ChatService chatService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = user(1L, "Alice");
        bob = user(2L, "Bob");
    }

    private static User user(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setRole(Role.TENANT);
        u.setEnabled(true);
        u.setPublicKey("public-key");
        return u;
    }

    private SendMessageRequestDto contentMessage(Long recipientId) {
        SendMessageRequestDto dto = new SendMessageRequestDto();
        dto.setRecipientId(recipientId);
        dto.setContent("ciphertext");
        dto.setNonce("nonce");
        return dto;
    }

    private SendMessageRequestDto attachmentMessage(Long recipientId) {
        SendMessageRequestDto dto = new SendMessageRequestDto();
        dto.setRecipientId(recipientId);
        // base64-encode a few bytes so decode() won't throw
        dto.setAttachmentData(Base64.getEncoder().encodeToString("file-bytes".getBytes()));
        dto.setAttachmentNonce("att-nonce");
        dto.setAttachmentMeta("att-meta");
        dto.setAttachmentMetaNonce("att-meta-nonce");
        return dto;
    }

    private void stubHappyPath() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(alice, bob)).thenReturn(false);
        when(userBlockRepository.existsByBlockerAndBlocked(bob, alice)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });
    }

    private Message existingMessage(User sender, User recipient) {
        return existingMessage(sender, recipient, LocalDateTime.now().minusMinutes(5));
    }

    private Message existingMessage(User sender, User recipient, LocalDateTime timestamp) {
        Message m = new Message();
        m.setId(10L);
        m.setSender(sender);
        m.setRecipient(recipient);
        m.setContent("old");
        m.setNonce("old-nonce");
        m.setTimestamp(timestamp);
        return m;
    }

    // ================================================================
    //  sendMessage
    // ================================================================

    @Nested
    @DisplayName("sendMessage")
    class SendMessageTests {

        @Test
        @DisplayName("success with content only → saves and pushes 'Sent you a message'")
        void successWithContentOnly() {
            stubHappyPath();

            Message sent = chatService.sendMessage(alice, contentMessage(2L));

            assertNotNull(sent);
            assertEquals("ciphertext", sent.getContent());
            verify(messageRepository).save(any(Message.class));
            verify(firebasePushService).sendToUser(eq(bob), eq("Alice"), eq("Sent you a message"), anyMap());
        }

        @Test
        @DisplayName("success with attachment only → pushes 'Sent you an attachment'")
        void successWithAttachmentOnly() {
            stubHappyPath();
            when(r2StorageService.upload(any(byte[].class))).thenReturn("storage-key");

            Message sent = chatService.sendMessage(alice, attachmentMessage(2L));

            assertNotNull(sent);
            assertEquals("", sent.getContent());  // empty content, not null
            verify(firebasePushService).sendToUser(eq(bob), eq("Alice"), eq("Sent you a photo"), anyMap());
        }

        @Test
        @DisplayName("success with content and attachment → saves both, attachment push takes priority")
        void successWithBoth() {
            stubHappyPath();
            when(r2StorageService.upload(any(byte[].class))).thenReturn("storage-key");

            SendMessageRequestDto dto = attachmentMessage(2L);
            dto.setContent("cipher");
            dto.setNonce("n");
            Message sent = chatService.sendMessage(alice, dto);

            assertNotNull(sent);
            assertEquals("cipher", sent.getContent());
            verify(firebasePushService).sendToUser(eq(bob), eq("Alice"), eq("Sent you a photo"), anyMap());
        }

        @Test
        @DisplayName("replyToMessageId is forwarded to the saved entity")
        void replyToMessageIdIsSet() {
            stubHappyPath();

            SendMessageRequestDto dto = contentMessage(2L);
            dto.setReplyToMessageId(42L);
            Message sent = chatService.sendMessage(alice, dto);

            assertEquals(42L, sent.getReplyToMessageId());
        }

        @Test
        @DisplayName("rejects empty message → BAD_REQUEST")
        void rejectsEmptyMessage() {
            SendMessageRequestDto dto = new SendMessageRequestDto();
            dto.setRecipientId(2L);

            ApiException ex = assertThrows(ApiException.class, () -> chatService.sendMessage(alice, dto));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        @DisplayName("rejects self-message → BAD_REQUEST")
        void rejectsSelfMessage() {
            ApiException ex = assertThrows(ApiException.class, () -> chatService.sendMessage(alice, contentMessage(1L)));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertTrue(ex.getMessage().contains("yourself"));
        }

        @Test
        @DisplayName("rejects when recipient not found → BAD_REQUEST")
        void rejectsRecipientNotFound() {
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class, () -> chatService.sendMessage(alice, contentMessage(2L)));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        @DisplayName("rejects oversized attachment → BAD_REQUEST")
        void rejectsOversizedAttachment() {
            SendMessageRequestDto dto = new SendMessageRequestDto();
            dto.setRecipientId(2L);
            dto.setAttachmentData("x".repeat(7_000_001));
            dto.setAttachmentNonce("n");
            dto.setAttachmentMeta("m");
            dto.setAttachmentMetaNonce("mn");

            ApiException ex = assertThrows(ApiException.class, () -> chatService.sendMessage(alice, dto));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertTrue(ex.getMessage().contains("too large"));
        }

        @Test
        @DisplayName("rejects when recipient disabled → BAD_REQUEST")
        void rejectsDisabledRecipient() {
            bob.setEnabled(false);
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

            ApiException ex = assertThrows(ApiException.class, () -> chatService.sendMessage(alice, contentMessage(2L)));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertTrue(ex.getMessage().contains("no longer available"));
        }

        @Test
        @DisplayName("rejects when recipient has no public key → BAD_REQUEST")
        void rejectsNoPublicKey() {
            bob.setPublicKey(null);
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(userBlockRepository.existsByBlockerAndBlocked(alice, bob)).thenReturn(false);
            when(userBlockRepository.existsByBlockerAndBlocked(bob, alice)).thenReturn(false);

            ApiException ex = assertThrows(ApiException.class, () -> chatService.sendMessage(alice, contentMessage(2L)));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertTrue(ex.getMessage().contains("secure messaging"));
        }

        @Test
        @DisplayName("idempotent replay: same nonce returns existing message")
        void idempotentReplay() {
            Message existing = existingMessage(alice, bob);
            existing.setId(99L);
            when(messageRepository.findBySenderAndNonce(alice, "nonce")).thenReturn(Optional.of(existing));

            Message result = chatService.sendMessage(alice, contentMessage(2L));

            assertEquals(99L, result.getId());
            verify(messageRepository, never()).save(any(Message.class));
        }
    }

    // ================================================================
    //  editMessage
    // ================================================================

    @Nested
    @DisplayName("editMessage")
    class EditMessageTests {

        @Test
        @DisplayName("success → edited=true, editedAt set, content updated")
        void editSuccess() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            Message edited = chatService.editMessage(alice, 10L, "new-cipher", "new-nonce");

            assertTrue(edited.isEdited());
            assertNotNull(edited.getEditedAt());
            assertEquals("new-cipher", edited.getContent());
            assertEquals("new-nonce", edited.getNonce());
        }

        @Test
        @DisplayName("non-owner rejected → FORBIDDEN")
        void nonOwnerRejected() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));

            ApiException ex = assertThrows(ApiException.class, () -> chatService.editMessage(bob, 10L, "x", "n"));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        @DisplayName("blank content rejected → BAD_REQUEST")
        void blankContentRejected() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));

            ApiException ex = assertThrows(ApiException.class, () -> chatService.editMessage(alice, 10L, "", "n"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        @DisplayName("edit window expired (>15min) → FORBIDDEN")
        void editWindowExpired() {
            Message msg = existingMessage(alice, bob, LocalDateTime.now().minusMinutes(20));
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));

            ApiException ex = assertThrows(ApiException.class, () -> chatService.editMessage(alice, 10L, "x", "n"));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
            assertTrue(ex.getMessage().contains("expired"));
        }

        @Test
        @DisplayName("message not found → NOT_FOUND")
        void messageNotFound() {
            when(messageRepository.findById(10L)).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class, () -> chatService.editMessage(alice, 10L, "x", "n"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }
    }

    // ================================================================
    //  deleteMessage
    // ================================================================

    @Nested
    @DisplayName("deleteMessage")
    class DeleteMessageTests {

        @Test
        @DisplayName("sender can delete")
        void senderCanDelete() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));

            chatService.deleteMessage(alice, 10L);

            verify(messageRepository).delete(msg);
        }

        @Test
        @DisplayName("recipient can delete")
        void recipientCanDelete() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));

            chatService.deleteMessage(bob, 10L);

            verify(messageRepository).delete(msg);
        }

        @Test
        @DisplayName("non-participant rejected → FORBIDDEN")
        void nonParticipantRejected() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));
            User stranger = user(99L, "Stranger");

            ApiException ex = assertThrows(ApiException.class, () -> chatService.deleteMessage(stranger, 10L));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        @DisplayName("attachment cleaned from R2 on delete")
        void attachmentCleaned() {
            Message msg = existingMessage(alice, bob);
            msg.setAttachmentStorageKey("some-key");
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));

            chatService.deleteMessage(alice, 10L);

            verify(r2StorageService).delete("some-key");
            verify(messageRepository).delete(msg);
        }

        @Test
        @DisplayName("message not found → NOT_FOUND")
        void messageNotFound() {
            when(messageRepository.findById(10L)).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class, () -> chatService.deleteMessage(alice, 10L));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }
    }

    // ================================================================
    //  toggleReaction
    // ================================================================

    @Nested
    @DisplayName("toggleReaction")
    class ToggleReactionTests {

        @Test
        @DisplayName("add → returns 'added'")
        void addReaction() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));
            when(reactionRepository.findByMessageAndUserAndEmoji(msg, alice, "👍")).thenReturn(Optional.empty());

            String action = chatService.toggleReaction(alice, 10L, "👍");

            assertEquals("added", action);
            verify(reactionRepository).save(any(MessageReaction.class));
        }

        @Test
        @DisplayName("toggle remove → returns 'removed'")
        void removeReaction() {
            Message msg = existingMessage(alice, bob);
            MessageReaction existing = new MessageReaction();
            existing.setId(5L);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));
            when(reactionRepository.findByMessageAndUserAndEmoji(msg, alice, "👍")).thenReturn(Optional.of(existing));

            String action = chatService.toggleReaction(alice, 10L, "👍");

            assertEquals("removed", action);
            verify(reactionRepository).delete(existing);
        }

        @Test
        @DisplayName("non-participant rejected → FORBIDDEN")
        void nonParticipantRejected() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));
            User stranger = user(99L, "Stranger");

            ApiException ex = assertThrows(ApiException.class, () -> chatService.toggleReaction(stranger, 10L, "👍"));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        @DisplayName("blank emoji rejected → BAD_REQUEST")
        void blankEmojiRejected() {
            Message msg = existingMessage(alice, bob);
            when(messageRepository.findById(10L)).thenReturn(Optional.of(msg));

            ApiException ex = assertThrows(ApiException.class, () -> chatService.toggleReaction(alice, 10L, ""));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    // ================================================================
    //  markAsRead
    // ================================================================

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTests {

        @Test
        @DisplayName("bulk UPDATE marks unread and returns count")
        void marksAndReturnsCount() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(messageRepository.markAllAsRead(bob, alice)).thenReturn(3);

            int count = chatService.markAsRead(alice, 2L);

            assertEquals(3, count);
            verify(messageRepository).markAllAsRead(bob, alice);
        }

        @Test
        @DisplayName("sender not found → BAD_REQUEST")
        void senderNotFound() {
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class, () -> chatService.markAsRead(alice, 2L));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    // ================================================================
    //  getUnreadCount
    // ================================================================

    @Nested
    @DisplayName("getUnreadCount")
    class UnreadCountTests {

        @Test
        @DisplayName("delegates to repository")
        void delegatesToRepo() {
            when(messageRepository.countUnreadNotBlocked(alice)).thenReturn(5L);
            assertEquals(5L, chatService.getUnreadCount(alice));
        }

        @Test
        @DisplayName("null → 0")
        void nullIsZero() {
            when(messageRepository.countUnreadNotBlocked(alice)).thenReturn(null);
            assertEquals(0L, chatService.getUnreadCount(alice));
        }
    }

    // ================================================================
    //  typing indicators
    // ================================================================

    @Nested
    @DisplayName("typing indicators")
    class TypingTests {

        @Test
        @DisplayName("recordTyping then isTyping returns true")
        void typingDetected() {
            chatService.recordTyping(1L, 2L);
            assertTrue(chatService.isTyping(1L, 2L));
        }

        @Test
        @DisplayName("isTyping returns false when nothing recorded")
        void noTyping() {
            assertFalse(chatService.isTyping(99L, 100L));
        }
    }

    // ================================================================
    //  clearChat / deleteChat
    // ================================================================

    @Nested
    @DisplayName("clearChat and deleteChat")
    class ClearDeleteChatTests {

        @Test
        @DisplayName("clearChat sets clearedBeforeMessageId")
        void clearChatSetsId() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            Message lastMsg = existingMessage(alice, bob);
            lastMsg.setId(50L);
            when(messageRepository.findLastMessage(alice, bob)).thenReturn(lastMsg);
            when(chatVisibilityRepository.findByUserAndPartner(alice, bob)).thenReturn(Optional.empty());
            when(chatVisibilityRepository.save(any(ChatVisibility.class))).thenAnswer(inv -> inv.getArgument(0));

            chatService.clearChat(alice, 2L);

            verify(chatVisibilityRepository).save(any(ChatVisibility.class));
        }

        @Test
        @DisplayName("deleteChat sets hiddenSinceMessageId")
        void deleteChatSetsId() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            Message lastMsg = existingMessage(alice, bob);
            lastMsg.setId(50L);
            when(messageRepository.findLastMessage(alice, bob)).thenReturn(lastMsg);
            when(chatVisibilityRepository.findByUserAndPartner(alice, bob)).thenReturn(Optional.empty());
            when(chatVisibilityRepository.save(any(ChatVisibility.class))).thenAnswer(inv -> inv.getArgument(0));

            chatService.deleteChat(alice, 2L);

            verify(chatVisibilityRepository).save(any(ChatVisibility.class));
        }
    }
}
