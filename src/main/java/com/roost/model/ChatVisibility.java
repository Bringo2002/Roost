package com.roost.model;

import jakarta.persistence.*;

/**
 * Per-user visibility state for a conversation with a specific partner.
 * Neither "clear chat" nor "delete chat" deletes anything from the
 * messages table -- the other person still needs their full history --
 * so both are expressed here as a cutoff from this user's point of view
 * only. One row per (user, partner) pair.
 */
@Entity
@Table(name = "chat_visibility", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "partner_id"}))
public class ChatVisibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "partner_id", nullable = false)
    private User partner;

    /**
     * "Clear chat": messages with id <= this are hidden from [user]'s
     * view of this conversation. Null means nothing has been cleared.
     * Set to the latest message id at the moment of clearing, so
     * messages that already existed disappear but new ones still show.
     */
    @Column(name = "cleared_before_message_id")
    private Long clearedBeforeMessageId;

    /**
     * "Delete chat": this conversation is hidden from [user]'s
     * conversation list as long as no message newer than this id
     * exists. The moment [partner] sends a new message, the
     * conversation reappears automatically -- there's no separate
     * "unhide" action, matching how WhatsApp/Telegram behave. Null
     * means the conversation isn't hidden.
     */
    @Column(name = "hidden_since_message_id")
    private Long hiddenSinceMessageId;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getPartner() {
        return partner;
    }

    public void setPartner(User partner) {
        this.partner = partner;
    }

    public Long getClearedBeforeMessageId() {
        return clearedBeforeMessageId;
    }

    public void setClearedBeforeMessageId(Long clearedBeforeMessageId) {
        this.clearedBeforeMessageId = clearedBeforeMessageId;
    }

    public Long getHiddenSinceMessageId() {
        return hiddenSinceMessageId;
    }

    public void setHiddenSinceMessageId(Long hiddenSinceMessageId) {
        this.hiddenSinceMessageId = hiddenSinceMessageId;
    }
}
