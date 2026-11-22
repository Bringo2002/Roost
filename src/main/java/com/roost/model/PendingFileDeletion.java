package com.roost.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A stored file that is waiting to be deleted.
 *
 * <p>Erasing an account queues its uploaded files here instead of deleting
 * them immediately; a nightly job removes each one once {@link #getDeleteAfter()}
 * has passed. A file appears at most once, whichever records referred to it.
 */
@Entity
@Table(name = "pending_file_deletion")
public class PendingFileDeletion {

    /** Which storage bucket the file lives in. */
    public enum Bucket {
        /** Publicly readable uploads: listing photos, profile photos, chat attachments. */
        PUBLIC,
        /** Private verification documents (IDs, deeds, utility bills). */
        PRIVATE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Bucket bucket;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "delete_after", nullable = false)
    private LocalDateTime deleteAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected PendingFileDeletion() {
    }

    public PendingFileDeletion(Bucket bucket, String storageKey, LocalDateTime deleteAfter) {
        this.bucket = bucket;
        this.storageKey = storageKey;
        this.deleteAfter = deleteAfter;
    }

    public Long getId() {
        return id;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public LocalDateTime getDeleteAfter() {
        return deleteAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
