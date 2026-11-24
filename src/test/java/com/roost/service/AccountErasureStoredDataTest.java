package com.roost.service;

import com.roost.model.Message;
import com.roost.model.MoveInInspection;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.model.VerificationDocument;
import com.roost.repository.ApplicationRepository;
import com.roost.repository.ChatVisibilityRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.MoveInInspectionRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.UserRepository;
import com.roost.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What account erasure does with the data an account stored: its uploaded
 * files, verification documents, messages and inspections. Run through both
 * the scheduled path and the legacy instant path.
 */
@ExtendWith(MockitoExtension.class)
class AccountErasureStoredDataTest {

    @Mock private UserRepository userRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationDocumentRepository verificationDocumentRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ChatVisibilityRepository chatVisibilityRepository;
    @Mock private MoveInInspectionRepository moveInInspectionRepository;
    @Mock private FileDeletionQueue fileDeletionQueue;

    @InjectMocks
    private AccountDeletionService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setAvatarUrl("https://files.example/avatars/ada.jpg");
    }

    @Test
    @DisplayName("the profile photo and every listing photo, document and video are queued for deletion")
    void uploadedFilesAreQueued() {
        Property listing = new Property();
        listing.setImageUrl("https://files.example/properties/cover.jpg");
        listing.setImageUrls(new ArrayList<>(List.of("https://files.example/properties/a.jpg")));
        listing.setDocumentUrls(new ArrayList<>(List.of("https://files.example/properties/deed.pdf")));
        listing.setVideoUrl("https://files.example/properties/tour.mp4");
        when(propertyRepository.findByOwner(user)).thenReturn(List.of(listing));

        service.executeIrreversibleDeletion(user);

        verify(fileDeletionQueue).queuePublicUrl("https://files.example/avatars/ada.jpg");
        verify(fileDeletionQueue).queuePublicUrl("https://files.example/properties/cover.jpg");
        verify(fileDeletionQueue).queuePublicUrl("https://files.example/properties/a.jpg");
        verify(fileDeletionQueue).queuePublicUrl("https://files.example/properties/deed.pdf");
        verify(fileDeletionQueue).queuePublicUrl("https://files.example/properties/tour.mp4");
        // The references are still cleared from the listing itself.
        assertEquals(0, listing.getImageUrls().size());
    }

    @Test
    @DisplayName("verification document files are queued, the AI analysis is cleared and the row is marked deleted")
    void verificationDocumentsAreErased() {
        VerificationDocument document = new VerificationDocument();
        document.setStorageKey("verification/id-front.jpg");
        document.setAiOpinion("Name on the ID: Ada Lovelace, number 12345678");
        when(verificationDocumentRepository.findByOwnerId(42L)).thenReturn(List.of(document));

        service.executeIrreversibleDeletion(user);

        verify(fileDeletionQueue).queuePrivateKey("verification/id-front.jpg");
        assertNull(document.getAiOpinion());
        assertNotNull(document.getDeletedAt());
        verify(verificationDocumentRepository).saveAll(List.of(document));
    }

    @Test
    @DisplayName("a document that was already marked deleted keeps its original deletion time")
    void alreadyDeletedDocumentKeepsItsDeletionTime() {
        LocalDateTime earlier = LocalDateTime.of(2026, 10, 1, 12, 0);
        VerificationDocument document = new VerificationDocument();
        document.setStorageKey("verification/old.jpg");
        document.setDeletedAt(earlier);
        when(verificationDocumentRepository.findByOwnerId(42L)).thenReturn(List.of(document));

        service.executeIrreversibleDeletion(user);

        assertEquals(earlier, document.getDeletedAt());
        verify(fileDeletionQueue).queuePrivateKey("verification/old.jpg");
    }

    @Test
    @DisplayName("messages are deleted, their attachment files queued and the chat settings removed")
    void messagesAreDeletedAndAttachmentsQueued() {
        Message withAttachment = new Message();
        withAttachment.setAttachmentStorageKey("chat/photo-1.jpg");
        Message textOnly = new Message();
        List<Message> messages = List.of(withAttachment, textOnly);
        when(messageRepository.findAllInvolving(user)).thenReturn(messages);

        service.executeIrreversibleDeletion(user);

        verify(fileDeletionQueue).queuePublicKey("chat/photo-1.jpg");
        verify(fileDeletionQueue, times(2)).queuePublicKey(any());
        verify(messageRepository).deleteAll(messages);
        verify(chatVisibilityRepository).deleteAllInvolving(user);
    }

    @Test
    @DisplayName("an inspection with no recorded tenant name gets the account's name before it is erased")
    void inspectionWithoutNameGetsTheAccountName() {
        MoveInInspection unnamed = new MoveInInspection();
        MoveInInspection named = new MoveInInspection();
        named.setTenantName("Name Recorded At The Time");
        when(moveInInspectionRepository.findByTenantOrderByCreatedAtDesc(user))
                .thenReturn(List.of(unnamed, named));

        service.executeIrreversibleDeletion(user);

        assertEquals("Ada Lovelace", unnamed.getTenantName());
        assertEquals("Name Recorded At The Time", named.getTenantName());
        assertEquals("Deleted user", user.getName());
        verify(moveInInspectionRepository).saveAll(List.of(unnamed));
    }

    @Test
    @DisplayName("inspections that already have a name are not touched")
    void namedInspectionsAreNotSaved() {
        MoveInInspection named = new MoveInInspection();
        named.setTenantName("Ada Lovelace");
        when(moveInInspectionRepository.findByTenantOrderByCreatedAtDesc(user)).thenReturn(List.of(named));

        service.executeIrreversibleDeletion(user);

        verify(moveInInspectionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("the legacy instant deletion erases the same stored data")
    void legacyInstantDeletionErasesTheSameData() {
        List<Message> messages = List.of(new Message());
        when(messageRepository.findAllInvolving(user)).thenReturn(messages);

        service.deleteAccount(user, "ada@example.com");

        verify(messageRepository).deleteAll(messages);
        verify(fileDeletionQueue).queuePublicUrl("https://files.example/avatars/ada.jpg");
    }
}
