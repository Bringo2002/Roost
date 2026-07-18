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
     * the server never has the keys needed to read this.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Base64-encoded nonce used for this message's encryption. Nullable
     * only to avoid breaking any pre-existing plaintext rows on deploy;
     * every message sent through the updated /api/chat endpoint will
     * always include one.
     */
    @Column(columnDefinition = "TEXT")
    private String nonce;

    private LocalDateTime timestamp = LocalDateTime.now();

    private boolean read = false;
}
