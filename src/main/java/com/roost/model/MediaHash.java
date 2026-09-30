package com.roost.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Immutable record mapping a SHA-256 content hash to an existing R2 object.
 * Used by the presigned upload flow to short-circuit duplicate uploads:
 * if the client sends a content hash that already exists here, the backend
 * returns the existing {@link #publicUrl} immediately, skipping the upload.
 *
 * <p>Rows are append-only — once a hash is recorded, it is never updated
 * or deleted (unless the corresponding R2 object is garbage-collected, which
 * is not yet implemented).
 */
@Entity
@Table(name = "media_hashes")
public class MediaHash {

    @Id
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "public_url", nullable = false, length = 512)
    private String publicUrl;

    @Column(name = "r2_key", nullable = false, length = 255)
    private String r2Key;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at")
    private Instant createdAt;

    /** JPA requires a no-arg constructor. */
    protected MediaHash() {}

    public MediaHash(String contentHash, String publicUrl, String r2Key,
                     String contentType, long sizeBytes) {
        this.contentHash = contentHash;
        this.publicUrl = publicUrl;
        this.r2Key = r2Key;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
    }

    public String getContentHash() { return contentHash; }
    public String getPublicUrl()   { return publicUrl; }
    public String getR2Key()       { return r2Key; }
    public String getContentType() { return contentType; }
    public long   getSizeBytes()   { return sizeBytes; }
    public Instant getCreatedAt()  { return createdAt; }
}
