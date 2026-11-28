package com.roost.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Typed request body for {@code POST /api/chat} (send a message).
 * Replaces the untyped {@code Map<String, Object>} the controller
 * previously accepted, so Spring's bean-validation layer rejects
 * malformed payloads before they reach the service.
 *
 * <p>A message needs encrypted content (content + nonce), an encrypted
 * attachment (attachmentData + attachmentNonce + attachmentMeta +
 * attachmentMetaNonce), or both. That cross-field rule is enforced in
 * {@link com.roost.service.ChatService#sendMessage} rather than here,
 * because bean-validation on individual fields can't express "at least
 * one of these two groups must be present."
 */
public class SendMessageRequestDto {

    @NotNull(message = "recipientId is required")
    private Long recipientId;

    @Size(max = 65_536, message = "content exceeds maximum length")
    private String content;

    @Size(max = 64, message = "nonce exceeds maximum length")
    private String nonce;

    private String attachmentData;  // size checked in service (base64 cap)

    @Size(max = 64, message = "attachmentNonce exceeds maximum length")
    private String attachmentNonce;

    @Size(max = 10_000, message = "attachmentMeta exceeds maximum length")
    private String attachmentMeta;

    @Size(max = 64, message = "attachmentMetaNonce exceeds maximum length")
    private String attachmentMetaNonce;

    private Long replyToMessageId;

    public SendMessageRequestDto() {}

    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getAttachmentData() { return attachmentData; }
    public void setAttachmentData(String attachmentData) { this.attachmentData = attachmentData; }

    public String getAttachmentNonce() { return attachmentNonce; }
    public void setAttachmentNonce(String attachmentNonce) { this.attachmentNonce = attachmentNonce; }

    public String getAttachmentMeta() { return attachmentMeta; }
    public void setAttachmentMeta(String attachmentMeta) { this.attachmentMeta = attachmentMeta; }

    public String getAttachmentMetaNonce() { return attachmentMetaNonce; }
    public void setAttachmentMetaNonce(String attachmentMetaNonce) { this.attachmentMetaNonce = attachmentMetaNonce; }

    public Long getReplyToMessageId() { return replyToMessageId; }
    public void setReplyToMessageId(Long replyToMessageId) { this.replyToMessageId = replyToMessageId; }
}

