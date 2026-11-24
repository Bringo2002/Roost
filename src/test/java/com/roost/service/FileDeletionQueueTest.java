package com.roost.service;

import com.roost.model.PendingFileDeletion;
import com.roost.model.PendingFileDeletion.Bucket;
import com.roost.repository.PendingFileDeletionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDeletionQueueTest {

    private static final String BASE = "https://files.example";

    @Mock private PendingFileDeletionRepository queue;
    @Mock private R2StorageService publicStorage;

    @InjectMocks
    private FileDeletionQueue fileQueue;

    @BeforeEach
    void setUp() {
        lenient().when(publicStorage.getPublicBaseUrl()).thenReturn(BASE);
    }

    private PendingFileDeletion savedEntry() {
        ArgumentCaptor<PendingFileDeletion> captor = ArgumentCaptor.forClass(PendingFileDeletion.class);
        verify(queue).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a public URL is queued by its storage key, 30 days out")
    void publicUrlIsQueuedByKeyThirtyDaysOut() {
        LocalDateTime before = LocalDateTime.now();

        fileQueue.queuePublicUrl(BASE + "/properties/photo.jpg");

        PendingFileDeletion entry = savedEntry();
        assertEquals(Bucket.PUBLIC, entry.getBucket());
        assertEquals("properties/photo.jpg", entry.getStorageKey());
        assertFalse(entry.getDeleteAfter().isBefore(before.plusDays(30)));
        assertTrue(entry.getDeleteAfter().isBefore(LocalDateTime.now().plusDays(30).plusSeconds(1)));
    }

    @Test
    @DisplayName("a query string on the URL is not part of the key")
    void queryStringIsNotPartOfTheKey() {
        fileQueue.queuePublicUrl(BASE + "/avatars/ada.jpg?v=3");

        assertEquals("avatars/ada.jpg", savedEntry().getStorageKey());
    }

    @Test
    @DisplayName("a URL that is not ours is ignored")
    void urlThatIsNotOursIsIgnored() {
        fileQueue.queuePublicUrl("https://lh3.googleusercontent.com/a/photo");

        verify(queue, never()).save(any());
    }

    @Test
    @DisplayName("a lookalike host is not treated as ours")
    void lookalikeHostIsIgnored() {
        fileQueue.queuePublicUrl("https://files.example.evil.test/properties/photo.jpg");

        verify(queue, never()).save(any());
    }

    @Test
    @DisplayName("null and blank values are ignored")
    void nullAndBlankValuesAreIgnored() {
        fileQueue.queuePublicUrl(null);
        fileQueue.queuePublicUrl("  ");
        fileQueue.queuePublicKey(null);
        fileQueue.queuePrivateKey("");

        verify(queue, never()).save(any());
    }

    @Test
    @DisplayName("nothing is queued when public storage has no base URL configured")
    void nothingIsQueuedWithoutABaseUrl() {
        when(publicStorage.getPublicBaseUrl()).thenReturn("");

        fileQueue.queuePublicUrl(BASE + "/properties/photo.jpg");

        verify(queue, never()).save(any());
    }

    @Test
    @DisplayName("a file that is already queued is not queued again")
    void alreadyQueuedFileIsNotQueuedAgain() {
        when(queue.existsByBucketAndStorageKey(Bucket.PUBLIC, "properties/photo.jpg")).thenReturn(true);

        fileQueue.queuePublicUrl(BASE + "/properties/photo.jpg");

        verify(queue, never()).save(any());
    }

    @Test
    @DisplayName("a private key is queued in the private bucket")
    void privateKeyIsQueuedInThePrivateBucket() {
        fileQueue.queuePrivateKey("verification/0b6f0c64-8f2c-4d57-9f5d-0e5f6f1f3a11.jpg");

        PendingFileDeletion entry = savedEntry();
        assertEquals(Bucket.PRIVATE, entry.getBucket());
        assertEquals("verification/0b6f0c64-8f2c-4d57-9f5d-0e5f6f1f3a11.jpg", entry.getStorageKey());
    }

    @Test
    @DisplayName("a public key is queued in the public bucket")
    void publicKeyIsQueuedInThePublicBucket() {
        fileQueue.queuePublicKey("chat/attachment-1.bin");

        assertEquals(Bucket.PUBLIC, savedEntry().getBucket());
    }
}
