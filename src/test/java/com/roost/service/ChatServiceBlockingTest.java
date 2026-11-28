package com.roost.service;

import com.roost.dto.ConversationSummaryDto;
import com.roost.dto.SendMessageRequestDto;
import com.roost.exception.ApiException;
import com.roost.model.Message;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.ChatVisibilityRepository;
import com.roost.repository.MessageReactionRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.UserBlockRepository;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * How blocking changes chat: a message is refused when either person has
 * blocked the other, and the person who blocked no longer sees the blocked
 * person's conversation, messages or unread count.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceBlockingTest {

    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageReactionRepository reactionRepository;
    @Mock private ChatVisibilityRepository chatVisibilityRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private R2StorageService r2StorageService;
    @Mock private FirebasePushService firebasePushService;

    @InjectMocks
    private ChatService chatService;

    private User me;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        me = user(1L, "Me");
        bob = user(2L, "Bob");
        carol = user(3L, "Carol");
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

    private SendMessageRequestDto messageTo(Long recipientId) {
        SendMessageRequestDto dto = new SendMessageRequestDto();
        dto.setRecipientId(recipientId);
        dto.setContent("ciphertext");
        dto.setNonce("nonce");
        return dto;
    }

    // -- sending --------------------------------------------------------

    @Test
    @DisplayName("you cannot message someone you blocked, and are told to unblock them")
    void cannotMessageSomeoneYouBlocked() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(me, bob)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> chatService.sendMessage(me, messageTo(2L)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Unblock this person to message them", ex.getMessage());
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(firebasePushService);
    }

    @Test
    @DisplayName("you cannot message someone who blocked you, and the reason is not spelled out")
    void cannotMessageSomeoneWhoBlockedYou() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(me, bob)).thenReturn(false);
        when(userBlockRepository.existsByBlockerAndBlocked(bob, me)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> chatService.sendMessage(me, messageTo(2L)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("You can't send messages to this person", ex.getMessage());
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(firebasePushService);
    }

    @Test
    @DisplayName("with no block either way, a message is sent as before")
    void messageIsSentWhenNobodyBlocked() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(me, bob)).thenReturn(false);
        when(userBlockRepository.existsByBlockerAndBlocked(bob, me)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        Message sent = chatService.sendMessage(me, messageTo(2L));

        assertNotNull(sent);
        verify(messageRepository).save(any(Message.class));
    }

    // -- history --------------------------------------------------------

    @Test
    @DisplayName("history with someone you blocked is empty and never reads their messages")
    void historyWithABlockedUserIsEmpty() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(me, bob)).thenReturn(true);

        List<Message> history = chatService.getChatHistory(me, 2L, null, null, null);

        assertTrue(history.isEmpty());
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("polling for new messages from someone you blocked also returns nothing")
    void pollingAfterABlockReturnsNothing() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(me, bob)).thenReturn(true);

        assertTrue(chatService.getChatHistory(me, 2L, null, 50L, null).isEmpty());
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("history with someone you did not block is read as before")
    void historyWithAnUnblockedUserIsRead() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(me, bob)).thenReturn(false);
        when(chatVisibilityRepository.findByUserAndPartner(me, bob)).thenReturn(Optional.empty());
        when(messageRepository.findChatHistoryLatestDesc(any(), any(), any(), any()))
                .thenReturn(new java.util.ArrayList<>());

        List<Message> history = chatService.getChatHistory(me, 2L, null, null, null);

        assertTrue(history.isEmpty());
        verify(messageRepository).findChatHistoryLatestDesc(any(), any(), any(), any());
    }

    // -- lists and counts -----------------------------------------------

    @Test
    @DisplayName("active chats leave out people you blocked")
    void activeChatsSkipBlockedPartners() {
        when(messageRepository.findActiveChatPartners(me)).thenReturn(List.of(bob, carol));
        when(userBlockRepository.findBlockedIdsByBlocker(me)).thenReturn(List.of(2L));
        when(messageRepository.findLastMessagesForPartners(me, List.of(3L))).thenReturn(List.of());

        List<User> active = chatService.getActiveChats(me);

        assertEquals(List.of(carol), active);
    }

    @Test
    @DisplayName("conversations leave out people you blocked")
    void conversationsSkipBlockedPartners() {
        when(messageRepository.findActiveChatPartners(me)).thenReturn(List.of(bob, carol));
        when(userBlockRepository.findBlockedIdsByBlocker(me)).thenReturn(List.of(2L));
        when(messageRepository.findLastMessagesForPartners(me, List.of(3L))).thenReturn(List.of());
        when(messageRepository.countUnreadFromUsers(List.of(3L), me)).thenReturn(List.of());

        List<ConversationSummaryDto> conversations = chatService.getConversations(me);

        assertEquals(1, conversations.size());
        assertEquals(3L, conversations.get(0).getPartner().getId());
    }

    @Test
    @DisplayName("with nobody blocked the lists are unchanged")
    void listsAreUnchangedWithNoBlocks() {
        when(messageRepository.findActiveChatPartners(me)).thenReturn(List.of(bob, carol));
        when(userBlockRepository.findBlockedIdsByBlocker(me)).thenReturn(List.of());
        when(messageRepository.findLastMessagesForPartners(me, List.of(2L, 3L))).thenReturn(List.of());

        assertEquals(List.of(bob, carol), chatService.getActiveChats(me));
    }

    @Test
    @DisplayName("the unread count leaves out people you blocked")
    void unreadCountSkipsBlockedSenders() {
        when(messageRepository.countUnreadNotBlocked(me)).thenReturn(4L);

        assertEquals(4L, chatService.getUnreadCount(me));
        verify(messageRepository, never()).countUnreadMessages(any());
    }

    @Test
    @DisplayName("a null unread count is zero")
    void nullUnreadCountIsZero() {
        when(messageRepository.countUnreadNotBlocked(me)).thenReturn(null);

        assertEquals(0L, chatService.getUnreadCount(me));
    }
}
