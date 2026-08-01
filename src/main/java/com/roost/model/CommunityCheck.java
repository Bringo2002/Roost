package com.roost.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A tenant's post-application confirmation of whether a listing matched
 * what was advertised. Gated on having actually applied to the
 * property (see PropertyService.submitCommunityCheck) -- a genuine
 * engagement signal, since Roost doesn't yet track scheduled in-person
 * viewings the way the original design assumed.
 */
@Entity
@Table(name = "community_checks")
public class CommunityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne
    @JoinColumn(name = "respondent_id", nullable = false)
    private User respondent;

    @Column(nullable = false)
    private boolean visited;

    private boolean photosAccurate;
    private boolean locationAccurate;
    private boolean priceAccurate;
    private boolean wouldRecommend;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public CommunityCheck() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public User getRespondent() {
        return respondent;
    }

    public void setRespondent(User respondent) {
        this.respondent = respondent;
    }

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public boolean isPhotosAccurate() {
        return photosAccurate;
    }

    public void setPhotosAccurate(boolean photosAccurate) {
        this.photosAccurate = photosAccurate;
    }

    public boolean isLocationAccurate() {
        return locationAccurate;
    }

    public void setLocationAccurate(boolean locationAccurate) {
        this.locationAccurate = locationAccurate;
    }

    public boolean isPriceAccurate() {
        return priceAccurate;
    }

    public void setPriceAccurate(boolean priceAccurate) {
        this.priceAccurate = priceAccurate;
    }

    public boolean isWouldRecommend() {
        return wouldRecommend;
    }

    public void setWouldRecommend(boolean wouldRecommend) {
        this.wouldRecommend = wouldRecommend;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
