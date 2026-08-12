package com.roost.dto;

import com.roost.model.Review;
import java.time.LocalDateTime;

/**
 * Response shape for submitReview / getPropertyReviews. getPropertyReviews
 * has no authentication at all, so this was the most exposed of the raw-
 * entity leaks in the sweep -- reviewer was a full User (email included)
 * on a fully public endpoint. Matches the frontend's flat `propertyId`
 * field (lib/models/review.dart reads that, not a nested property
 * object), derived from the entity's Property relation rather than
 * serializing that relation directly.
 */
public class ReviewResponseDto {

    private final Long id;
    private final Long propertyId;
    private final MiniUserDto reviewer;
    private final int rating;
    private final String comment;
    private final LocalDateTime createdAt;

    public ReviewResponseDto(Review r) {
        this.id = r.getId();
        this.propertyId = r.getProperty() != null ? r.getProperty().getId() : null;
        this.reviewer = MiniUserDto.from(r.getReviewer());
        this.rating = r.getRating();
        this.comment = r.getComment();
        this.createdAt = r.getCreatedAt();
    }

    public static ReviewResponseDto from(Review r) {
        return r == null ? null : new ReviewResponseDto(r);
    }

    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public MiniUserDto getReviewer() { return reviewer; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
