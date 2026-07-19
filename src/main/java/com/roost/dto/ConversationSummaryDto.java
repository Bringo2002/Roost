package com.roost.dto;

import com.roost.model.User;
import java.time.LocalDateTime;

public class ConversationSummaryDto {
    private User partner;
    private String lastMessageContent;
    private String lastMessageNonce;
    private LocalDateTime lastMessageTimestamp;
    private Long lastMessageSenderId;
    private long unreadCount;
    private String lastMessageAttachmentMeta;
    private String lastMessageAttachmentMetaNonce;
    private boolean hasAttachment;

    public ConversationSummaryDto() {}

    public ConversationSummaryDto(User partner, String lastMessageContent, String lastMessageNonce, 
                                  LocalDateTime lastMessageTimestamp, Long lastMessageSenderId, 
                                  long unreadCount, String lastMessageAttachmentMeta, 
                                  String lastMessageAttachmentMetaNonce, boolean hasAttachment) {
        this.partner = partner;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageNonce = lastMessageNonce;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.lastMessageSenderId = lastMessageSenderId;
        this.unreadCount = unreadCount;
        this.lastMessageAttachmentMeta = lastMessageAttachmentMeta;
        this.lastMessageAttachmentMetaNonce = lastMessageAttachmentMetaNonce;
        this.hasAttachment = hasAttachment;
    }

    public User getPartner() {
        return partner;
    }

    public void setPartner(User partner) {
        this.partner = partner;
    }

    public String getLastMessageContent() {
        return lastMessageContent;
    }

    public void setLastMessageContent(String lastMessageContent) {
        this.lastMessageContent = lastMessageContent;
    }

    public String getLastMessageNonce() {
        return lastMessageNonce;
    }

    public void setLastMessageNonce(String lastMessageNonce) {
        this.lastMessageNonce = lastMessageNonce;
    }

    public LocalDateTime getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public void setLastMessageTimestamp(LocalDateTime lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public Long getLastMessageSenderId() {
        return lastMessageSenderId;
    }

    public void setLastMessageSenderId(Long lastMessageSenderId) {
        this.lastMessageSenderId = lastMessageSenderId;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }

    public String getLastMessageAttachmentMeta() {
        return lastMessageAttachmentMeta;
    }

    public void setLastMessageAttachmentMeta(String lastMessageAttachmentMeta) {
        this.lastMessageAttachmentMeta = lastMessageAttachmentMeta;
    }

    public String getLastMessageAttachmentMetaNonce() {
        return lastMessageAttachmentMetaNonce;
    }

    public void setLastMessageAttachmentMetaNonce(String lastMessageAttachmentMetaNonce) {
        this.lastMessageAttachmentMetaNonce = lastMessageAttachmentMetaNonce;
    }

    public boolean isHasAttachment() {
        return hasAttachment;
    }

    public void setHasAttachment(boolean hasAttachment) {
        this.hasAttachment = hasAttachment;
    }
}
