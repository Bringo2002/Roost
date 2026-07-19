package com.roost.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Column
    private boolean edited = false;

    @Column
    private LocalDateTime editedAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<MessageReaction> reactions = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String nonce;

    @Column(columnDefinition = "TEXT")
    private String attachmentStorageKey;

    @Transient
    private String attachmentData;

    @Column(columnDefinition = "TEXT")
    private String attachmentNonce;

    @Column(columnDefinition = "TEXT")
    private String attachmentMeta;

    @Column(columnDefinition = "TEXT")
    private String attachmentMetaNonce;

    private LocalDateTime timestamp = LocalDateTime.now();

    private boolean read = false;

    public Message() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(Long replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }

    public List<MessageReaction> getReactions() {
        return reactions;
    }

    public void setReactions(List<MessageReaction> reactions) {
        this.reactions = reactions;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getAttachmentStorageKey() {
        return attachmentStorageKey;
    }

    public void setAttachmentStorageKey(String attachmentStorageKey) {
        this.attachmentStorageKey = attachmentStorageKey;
    }

    public String getAttachmentData() {
        return attachmentData;
    }

    public void setAttachmentData(String attachmentData) {
        this.attachmentData = attachmentData;
    }

    public String getAttachmentNonce() {
        return attachmentNonce;
    }

    public void setAttachmentNonce(String attachmentNonce) {
        this.attachmentNonce = attachmentNonce;
    }

    public String getAttachmentMeta() {
        return attachmentMeta;
    }

    public void setAttachmentMeta(String attachmentMeta) {
        this.attachmentMeta = attachmentMeta;
    }

    public String getAttachmentMetaNonce() {
        return attachmentMetaNonce;
    }

    public void setAttachmentMetaNonce(String attachmentMetaNonce) {
        this.attachmentMetaNonce = attachmentMetaNonce;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean hasAttachment() {
        return attachmentStorageKey != null && !attachmentStorageKey.isBlank();
    }
}
