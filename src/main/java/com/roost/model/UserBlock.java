package com.roost.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One user blocking another. Blocking is one-way: if both people block each
 * other there are two rows. A pair can be blocked at most once.
 */
@Entity
@Table(name = "user_blocks", uniqueConstraints = @UniqueConstraint(
        name = "uq_user_blocks_pair", columnNames = {"blocker_id", "blocked_id"}))
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected UserBlock() {
    }

    /**
     * Creates a block.
     *
     * @param blocker the user who does the blocking
     * @param blocked the user being blocked
     */
    public UserBlock(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    public Long getId() {
        return id;
    }

    public User getBlocker() {
        return blocker;
    }

    public User getBlocked() {
        return blocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
