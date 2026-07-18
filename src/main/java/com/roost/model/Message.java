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
     * Object key for this attachment's encrypted bytes in Cloudflare R2
     * (see R2StorageService) -- e.g. "attachments/&lt;uuid&gt;". Only a
     * small reference lives in Postgres; the actual (still encrypted)
     * file content lives in object storage.
     */
    @Column(columnDefinition = "TEXT")
    private String attachmentStorageKey;

    /**
     * Base64-encoded ciphertext of the attachment's raw bytes. NOT
     * persisted -- this is populated on the way out (ChatController reads
     * the bytes from R2 via [attachmentStorageKey] and base64-encodes
     * them here) and read on the way in (ChatController uploads the
     * decoded bytes to R2 and discards this field before saving). Kept as
     * a real field, not a DTO, so the JSON wire shape for clients is
     * unchanged from before this moved out of Postgres.
     */
    @Transient
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

    public boolean hasAttachment() {
        return attachmentStorageKey != null && !attachmentStorageKey.isBlank();
    }
}
