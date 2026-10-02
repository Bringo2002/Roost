package com.roost.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Precomputed ranking score for one listing, refreshed by a scheduled job.
 * Stored (rather than computed per request) so a paginated feed can
 * {@code ORDER BY} it and page stably; re-sorting each page after it has
 * been fetched cannot change which listings appear first.
 *
 * <p>Kept in its own table, sharing the property's id as its primary key,
 * so the large {@link Property} entity and its serialization are untouched
 * and the hourly rewrite of every score does not churn the properties
 * table. Deleting a property removes its score at the database level.
 */
@Entity
@Table(name = "listing_rank_scores")
public class ListingRankScore {

    @Id
    @Column(name = "property_id")
    private Long propertyId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "property_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Property property;

    /** Final ordering value: higher ranks first. Includes {@link #exposureBoost}. */
    @Column(name = "score", nullable = false)
    private double score;

    /** Part of {@link #score} granted for under-exposure; stored so it can be audited. */
    @Column(name = "exposure_boost", nullable = false)
    private double exposureBoost;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    /** JPA requires a no-arg constructor. */
    protected ListingRankScore() {}

    /**
     * @throws IllegalArgumentException if a value is NaN/infinite or the boost is negative --
     *         one NaN would poison {@code ORDER BY score} for the whole feed
     */
    public ListingRankScore(Property property, double score, double exposureBoost, Instant computedAt) {
        this.property = property;
        update(score, exposureBoost, computedAt);
    }

    /** Replaces the stored values with a fresh computation; same validation as the constructor. */
    public final void update(double score, double exposureBoost, Instant computedAt) {
        if (!Double.isFinite(score) || !Double.isFinite(exposureBoost)) {
            throw new IllegalArgumentException("score and exposureBoost must be finite numbers");
        }
        if (exposureBoost < 0) {
            throw new IllegalArgumentException("exposureBoost must not be negative");
        }
        this.score = score;
        this.exposureBoost = exposureBoost;
        this.computedAt = computedAt;
    }

    public Long getPropertyId()       { return propertyId; }
    public Property getProperty()     { return property; }
    public double getScore()          { return score; }
    public double getExposureBoost()  { return exposureBoost; }
    public Instant getComputedAt()    { return computedAt; }
}
