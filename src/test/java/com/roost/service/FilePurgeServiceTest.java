package com.roost.service;

import com.roost.model.PendingFileDeletion;
import com.roost.model.PendingFileDeletion.Bucket;
import com.roost.repository.MediaHashRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.PendingFileDeletionRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilePurgeServiceTest {

    private static final String BASE = "https://files.example";
    private static final String KEY = "properties/photo.jpg";
    private static final String URL = BASE + "/" + KEY;

    @Mock private PendingFileDeletionRepository queue;
    @Mock private R2StorageService publicStorage;
    @Mock private PrivateDocumentStorage privateStorage;
    @Mock private PropertyRepository propertyRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private MediaHashRepository mediaHashRepository;

    @InjectMocks
    private FilePurgeService service;

    private PendingFileDeletion publicEntry;

    @BeforeEach
    void setUp() {
        lenient().when(publicStorage.getPublicBaseUrl()).thenReturn(BASE);
        publicEntry = new PendingFileDeletion(Bucket.PUBLIC, KEY, LocalDateTime.now().minusDays(1));
    }

    @Test
    @DisplayName("an unused public file is deleted, its upload record forgotten and it leaves the queue")
    void unusedPublicFileIsDeleted() {
        FilePurgeService.Outcome outcome = service.purge(publicEntry);

        assertEquals(FilePurgeService.Outcome.DELETED, outcome);
        verify(publicStorage).delete(KEY);
        verify(mediaHashRepository).deleteByR2Key(KEY);
        verify(queue).delete(publicEntry);
    }

    @Test
    @DisplayName("a file a listing still uses is kept, and only taken off the queue")
    void fileUsedByAListingIsKept() {
        when(propertyRepository.isMediaUrlInUse(URL)).thenReturn(true);

        FilePurgeService.Outcome outcome = service.purge(publicEntry);

        assertEquals(FilePurgeService.Outcome.KEPT_IN_USE, outcome);
        verify(publicStorage, never()).delete(any());
        verify(mediaHashRepository, never()).deleteByR2Key(any());
        verify(queue).delete(publicEntry);
    }

    @Test
    @DisplayName("a file another account uses as its profile photo is kept")
    void fileUsedAsAnotherAvatarIsKept() {
        when(userRepository.existsByAvatarUrl(URL)).thenReturn(true);

        assertEquals(FilePurgeService.Outcome.KEPT_IN_USE, service.purge(publicEntry));
        verify(publicStorage, never()).delete(any());
        verify(queue).delete(publicEntry);
    }

    @Test
    @DisplayName("a file a message still has as an attachment is kept")
    void fileUsedAsAnAttachmentIsKept() {
        when(messageRepository.existsByAttachmentStorageKey(KEY)).thenReturn(true);

        assertEquals(FilePurgeService.Outcome.KEPT_IN_USE, service.purge(publicEntry));
        verify(publicStorage, never()).delete(any());
        verify(queue).delete(publicEntry);
    }

    @Test
    @DisplayName("a private document is deleted from the private bucket without the sharing checks")
    void privateDocumentIsDeletedFromThePrivateBucket() {
        PendingFileDeletion privateEntry =
                new PendingFileDeletion(Bucket.PRIVATE, "verification/id.jpg", LocalDateTime.now().minusDays(1));

        FilePurgeService.Outcome outcome = service.purge(privateEntry);

        assertEquals(FilePurgeService.Outcome.DELETED, outcome);
        verify(privateStorage).delete("verification/id.jpg");
        verify(queue).delete(privateEntry);
        verifyNoInteractions(publicStorage, propertyRepository, userRepository, messageRepository, mediaHashRepository);
    }

    @Test
    @DisplayName("a failed storage delete leaves the file queued so it is retried")
    void failedStorageDeleteLeavesTheFileQueued() {
        doThrow(new RuntimeException("storage unavailable")).when(publicStorage).delete(KEY);

        assertThrows(RuntimeException.class, () -> service.purge(publicEntry));

        verify(queue, never()).delete(any(PendingFileDeletion.class));
        verify(mediaHashRepository, never()).deleteByR2Key(any());
    }

    @Test
    @DisplayName("nothing is deleted when the public base URL is not configured")
    void nothingIsDeletedWithoutABaseUrl() {
        when(publicStorage.getPublicBaseUrl()).thenReturn("");

        assertThrows(IllegalStateException.class, () -> service.purge(publicEntry));

        verify(publicStorage, never()).delete(any());
        verify(queue, never()).delete(any(PendingFileDeletion.class));
    }

    @Test
    @DisplayName("findDue returns what the queue says is due")
    void findDueReturnsTheDueEntries() {
        when(queue.findByDeleteAfterLessThanEqualOrderByDeleteAfterAscIdAsc(any(LocalDateTime.class)))
                .thenReturn(List.of(publicEntry));

        assertEquals(List.of(publicEntry), service.findDue());
    }
}
