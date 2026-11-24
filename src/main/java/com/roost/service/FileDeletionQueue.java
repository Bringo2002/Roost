package com.roost.service;

import com.roost.model.PendingFileDeletion;
import com.roost.model.PendingFileDeletion.Bucket;
import com.roost.repository.PendingFileDeletionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Puts stored files on the list of files to delete, to be removed
 * {@value #RETENTION_DAYS} days later by a nightly job.
 *
 * <p>Account erasure uses it for the files an account uploaded. The wait
 * is the retention period the privacy policy states. A file is queued once,
 * however many records referred to it, and anything that is not one of our
 * own uploads (for example a Google profile photo) is ignored.
 */
@Service
public class FileDeletionQueue {

    /** How long a file stays in storage after its owner's account is erased. */
    static final int RETENTION_DAYS = 30;

    private final PendingFileDeletionRepository queue;
    private final R2StorageService publicStorage;

    public FileDeletionQueue(PendingFileDeletionRepository queue, R2StorageService publicStorage) {
        this.queue = queue;
        this.publicStorage = publicStorage;
    }

    /**
     * Queues the public file behind a URL we issued. Blank values and URLs
     * that do not point at our own public storage are ignored.
     */
    public void queuePublicUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String base = publicStorage.getPublicBaseUrl();
        if (base == null || base.isBlank() || !url.startsWith(base + "/")) {
            return;
        }
        queuePublicKey(url.substring(base.length() + 1));
    }

    /** Queues a file in the public bucket by its storage key. */
    public void queuePublicKey(String key) {
        enqueue(Bucket.PUBLIC, key);
    }

    /** Queues a file in the private verification-documents bucket by its storage key. */
    public void queuePrivateKey(String key) {
        enqueue(Bucket.PRIVATE, key);
    }

    private void enqueue(Bucket bucket, String key) {
        if (key == null) {
            return;
        }
        int queryStart = key.indexOf('?');
        String cleanKey = queryStart >= 0 ? key.substring(0, queryStart) : key;
        if (cleanKey.isBlank() || queue.existsByBucketAndStorageKey(bucket, cleanKey)) {
            return;
        }
        queue.save(new PendingFileDeletion(bucket, cleanKey, LocalDateTime.now().plusDays(RETENTION_DAYS)));
    }
}
