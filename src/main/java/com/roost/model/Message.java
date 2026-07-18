package com.roost.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /**
     * Base64-encoded ciphertext. Encrypted client-side with the sender's
     * private key + recipient's public key (X25519 + ChaCha20-Poly1305) —
     * the server never has the keys needed to read this. Nullable because
     * an attachment-only message (no caption) has no text content.
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * Base64-encoded nonce used for [content]'s encryption. Nullable both
     * for attachment-only messages and to avoid breaking any pre-existing
     * plaintext rows on deploy.
     */
    @Column(columnDefinition = "TEXT")
    private String nonce;

    /**
     * Base64-encoded ciphertext of the attached file's raw bytes, encrypted
     * the same way as [content]. Stored as TEXT (base64) rather than a
     * binary column to keep this consistent with the rest of the schema;
     * the server stores an opaque blob and can never read the file.
     * MVP tradeoff: files live in Postgres, not object storage, so this
     * is only suitable for small attachments (size-capped client- and
     * server-side) -- worth moving to S3/Cloudinary/etc if usage grows.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String attachmentData;

    /** Nonce for [attachmentData]. Must differ from [nonce]. */
    @Column(columnDefinition = "TEXT")
    private String attachmentNonce;

    /**
     * Base64-encoded ciphertext of a small JSON blob
     * ({"name":..,"mimeType":..,"size":..}) describing the attachment.
     * Encrypted too, so the server can't see the filename or file type.
     */
    @Column(columnDefinition = "TEXT")
    private String attachmentMeta;

    /** Nonce for [attachmentMeta]. Must differ from both other nonces. */
    @Column(columnDefinition = "TEXT")
    private String attachmentMetaNonce;

    private LocalDateTime timestamp = LocalDateTime.now();

    private boolean read = false;
}
