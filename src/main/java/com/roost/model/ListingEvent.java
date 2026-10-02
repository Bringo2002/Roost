package com.roost.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Append-only record of one tenant interaction with one listing. This is
 * the raw signal that feeds listing ranking and exposure fairness: without
 * knowing what was <em>shown</em> (impressions) as well as what was acted
 * on, a listing's engagement rate cannot be computed.
 *
 * <p>The actor is either a signed-in user ({@link #userId}) or an anonymous
 * device ({@link #anonymousId}); at least one must be present. {@code userId}
 * is a plain column rather than an association on purpose: this is a
 * high-volume log, and a user deletion should not cascade into (or be
 * blocked by) analytics rows.
 *
 * <p>Rows belong to their listing: deleting a property removes its events
 * at the database level ({@code ON DELETE CASCADE}).
 */
@Entity
@Table(
        name = "listing_events",
        indexes = {
                @Index(name = "idx_listing_events_property_created", columnList = "property_id, created_at"),
                @Index(name = "idx_listing_events_created", columnList = "created_at")
        })
public class ListingEvent {

    /** Max length of {@link #anonymousId}; mirrors the column definition. */
    public static final int ANONYMOUS_ID_MAX_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Property property;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = ANONYMOUS_ID_MAX_LENGTH)
    private String anonymousId;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** JPA requires a no-arg constructor. */
    protected ListingEvent() {}

    /**
     * @param property    the listing the event is about
     * @param userId      signed-in actor, or null
     * @param anonymousId anonymous device actor, or null/blank when {@code userId} is set
     * @param type        what happened
     * @param createdAt   when it happened (callers pass {@code Instant.now()}; tests pass fixed times)
     * @throws IllegalArgumentException if neither actor is present or {@code anonymousId} is too long
     */
    public ListingEvent(Property property, Long userId, String anonymousId,
                        ListingEventType type, Instant createdAt) {
        boolean hasAnonymous = anonymousId != null && !anonymousId.isBlank();
        if (userId == null && !hasAnonymous) {
            throw new IllegalArgumentException("A listing event needs a userId or an anonymousId");
        }
        if (hasAnonymous && anonymousId.length() > ANONYMOUS_ID_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "anonymousId exceeds " + ANONYMOUS_ID_MAX_LENGTH + " characters");
        }
        this.property = property;
        this.userId = userId;
        this.anonymousId = hasAnonymous ? anonymousId : null;
        this.eventType = type.name();
        this.createdAt = createdAt;
    }

    public Long getId()             { return id; }
    public Property getProperty()   { return property; }
    public Long getUserId()         { return userId; }
    public String getAnonymousId()  { return anonymousId; }
    public String getEventType()    { return eventType; }
    public Instant getCreatedAt()   { return createdAt; }
}
