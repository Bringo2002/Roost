package com.roost.service;

import com.roost.model.PendingFileDeletion;
import com.roost.model.PendingFileDeletion.Bucket;
import com.roost.repository.MediaHashRepository;
import com.roost.repository.MessageRepository;
import com.roost.repository.PendingFileDeletionRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Deletes the stored files that were queued when an account was erased, once
 * their retention period is over.
 *
 * <p>Uploads are de-duplicated across users, so a queued public file can be
 * shared with another listing, profile photo or chat message. Those are
 * checked just before deleting, and a file that is still in use is kept and
 * simply taken off the queue.
 *
 * <p>The storage delete runs first. If it fails the exception rolls the
 * transaction back, the file stays queued, and the next nightly run tries it
 * again; deleting a file that is already gone is not an error.
 */
@Service
public class FilePurgeService {

    /** What happened to one queued file. */
    public enum Outcome {
        /** The file was removed from storage. */
        DELETED,
        /** Something else still uses the file, so it was left in place. */
        KEPT_IN_USE
    }

    private final PendingFileDeletionRepository queue;
    private final R2StorageService publicStorage;
    private final PrivateDocumentStorage privateStorage;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final MediaHashRepository mediaHashRepository;

    public FilePurgeService(PendingFileDeletionRepository queue,
                            R2StorageService publicStorage,
                            PrivateDocumentStorage privateStorage,
                            PropertyRepository propertyRepository,
                            UserRepository userRepository,
                            MessageRepository messageRepository,
                            MediaHashRepository mediaHashRepository) {
        this.queue = queue;
        this.publicStorage = publicStorage;
        this.privateStorage = privateStorage;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.mediaHashRepository = mediaHashRepository;
    }

    /** The queued files whose retention period is over, oldest first. */
    public List<PendingFileDeletion> findDue() {
        return queue.findByDeleteAfterLessThanEqualOrderByDeleteAfterAscIdAsc(LocalDateTime.now());
    }

    /**
     * Deletes one queued file, or keeps it if something else still uses it,
     * and takes it off the queue. Runs in its own transaction so one failure
     * does not affect the other files.
     *
     * @throws RuntimeException if the storage delete fails; the file stays queued
     */
    @Transactional
    public Outcome purge(PendingFileDeletion entry) {
        String key = entry.getStorageKey();
        if (entry.getBucket() == Bucket.PRIVATE) {
            privateStorage.delete(key);
            queue.delete(entry);
            return Outcome.DELETED;
        }

        if (isPublicFileInUse(key)) {
            queue.delete(entry);
            return Outcome.KEPT_IN_USE;
        }
        publicStorage.delete(key);
        mediaHashRepository.deleteByR2Key(key);
        queue.delete(entry);
        return Outcome.DELETED;
    }

    private boolean isPublicFileInUse(String key) {
        String base = publicStorage.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            // Without the base URL a listing's reference cannot be recognised,
            // so deleting could break someone else's photo. Leave it queued.
            throw new IllegalStateException("Public storage base URL is not configured");
        }
        String url = base + "/" + key;
        return propertyRepository.isMediaUrlInUse(url)
                || userRepository.existsByAvatarUrl(url)
                || messageRepository.existsByAttachmentStorageKey(key);
    }
}
