package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.ChatVisibilityRepository;
import com.roost.repository.MessageReactionRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Messages can't be sent to a deleted account. Deletion disables the account
 * and wipes its public key, so the recipient check has to say the account is
 * gone rather than blame a missing key.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceDeletedRecipientTest {

    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageReactionRepository reactionRepository;
    @Mock private ChatVisibilityRepository chatVisibilityRepository;
    @Mock private R2StorageService r2StorageService;
    @Mock private FirebasePushService firebasePushService;

    @InjectMocks
    private ChatService chatService;

    private User sender;
    private User recipient;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setId(1L);
        sender.setRole(Role.TENANT);

        recipient = new User();
        recipient.setId(2L);
        recipient.setRole(Role.LANDLORD);

        payload = Map.of("recipientId", 2L, "content", "ciphertext", "nonce", "nonce");
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
    }

    @Test
    @DisplayName("rejects a message to a deleted account and says the account is gone")
    void rejectsMessageToDisabledAccount() {
        recipient.setEnabled(false);
        recipient.setPublicKey(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> chatService.sendMessage(sender, payload));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("This account is no longer available", ex.getMessage());
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("an active recipient without a key still gets the secure-messaging message")
    void activeRecipientWithoutKeyKeepsTheExistingMessage() {
        recipient.setEnabled(true);
        recipient.setPublicKey(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> chatService.sendMessage(sender, payload));

        assertEquals("Recipient hasn't enabled secure messaging yet", ex.getMessage());
        verify(messageRepository, never()).save(any());
    }
}
