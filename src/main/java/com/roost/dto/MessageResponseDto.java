package com.roost.dto;

import com.roost.model.Message;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shape for sendMessage/getChatHistory/editMessage. Mirrors
 * Message field-for-field (matching the frontend's Message.fromJson
 * exactly -- see lib/models/message.dart) except sender/recipient go
 * through ChatUserDto and reactions go through
 * MessageReactionResponseDto, instead of serializing the raw User
 * entities each of those otherwise pulls in. attachmentStorageKey is
 * deliberately omitted -- it's an internal R2 key, not something the
 * client reads (it works from attachmentData, populated separately by
 * ChatService before this DTO is built).
 */
public class MessageResponseDto {

    private final Long id;
    private final ChatUserDto sender;
    private final ChatUserDto recipient;
    private final String content;
    private final String nonce;
    private final LocalDateTime timestamp;
    private final boolean read;
    private final Long replyToMessageId;
    private final boolean edited;
    private final LocalDateTime editedAt;
    private final List<MessageReactionResponseDto> reactions;
    private final String attachmentData;
    private final String attachmentNonce;
    private final String attachmentMeta;
    private final String attachmentMetaNonce;

    public MessageResponseDto(Message m) {
        this.id = m.getId();
        this.sender = ChatUserDto.from(m.getSender());
        this.recipient = ChatUserDto.from(m.getRecipient());
        this.content = m.getContent();
        this.nonce = m.getNonce();
        this.timestamp = m.getTimestamp();
        this.read = m.isRead();
        this.replyToMessageId = m.getReplyToMessageId();
        this.edited = m.isEdited();
        this.editedAt = m.getEditedAt();
        this.reactions = m.getReactions().stream().map(MessageReactionResponseDto::new).toList();
        this.attachmentData = m.getAttachmentData();
        this.attachmentNonce = m.getAttachmentNonce();
        this.attachmentMeta = m.getAttachmentMeta();
        this.attachmentMetaNonce = m.getAttachmentMetaNonce();
    }

    public static MessageResponseDto from(Message m) {
        return m == null ? null : new MessageResponseDto(m);
    }

    public static List<MessageResponseDto> from(List<Message> messages) {
        return messages.stream().map(MessageResponseDto::new).toList();
    }

    public Long getId() { return id; }
    public ChatUserDto getSender() { return sender; }
    public ChatUserDto getRecipient() { return recipient; }
    public String getContent() { return content; }
    public String getNonce() { return nonce; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public boolean isRead() { return read; }
    public Long getReplyToMessageId() { return replyToMessageId; }
    public boolean isEdited() { return edited; }
    public LocalDateTime getEditedAt() { return editedAt; }
    public List<MessageReactionResponseDto> getReactions() { return reactions; }
    public String getAttachmentData() { return attachmentData; }
    public String getAttachmentNonce() { return attachmentNonce; }
    public String getAttachmentMeta() { return attachmentMeta; }
    public String getAttachmentMetaNonce() { return attachmentMetaNonce; }
}
