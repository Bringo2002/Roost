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

    private Boolean visited = false;
    private Boolean photosAccurate = false;
    private Boolean locationAccurate = false;
    private Boolean priceAccurate = false;
    private Boolean wouldRecommend = false;

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
        return visited != null && visited;
    }

    public void setVisited(Boolean visited) {
        this.visited = visited;
    }

    public boolean isPhotosAccurate() {
        return photosAccurate != null && photosAccurate;
    }

    public void setPhotosAccurate(Boolean photosAccurate) {
        this.photosAccurate = photosAccurate;
    }

    public boolean isLocationAccurate() {
        return locationAccurate != null && locationAccurate;
    }

    public void setLocationAccurate(Boolean locationAccurate) {
        this.locationAccurate = locationAccurate;
    }

    public boolean isPriceAccurate() {
        return priceAccurate != null && priceAccurate;
    }

    public void setPriceAccurate(Boolean priceAccurate) {
        this.priceAccurate = priceAccurate;
    }

    public boolean isWouldRecommend() {
        return wouldRecommend != null && wouldRecommend;
    }

    public void setWouldRecommend(Boolean wouldRecommend) {
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
