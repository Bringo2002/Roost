package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.model.Review;
import com.roost.model.User;
import com.roost.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final PropertyService propertyService;

    public ReviewService(ReviewRepository reviewRepository, PropertyService propertyService) {
        this.reviewRepository = reviewRepository;
        this.propertyService = propertyService;
    }

    public Review submitReview(User reviewer, Map<String, Object> payload) {
        Long propertyId = Long.valueOf(payload.get("propertyId").toString());
        int rating = Integer.parseInt(payload.get("rating").toString());
        String comment = payload.get("comment") != null ? payload.get("comment").toString() : "";

        if (rating < 1 || rating > 5) {
            throw ApiException.badRequest("Rating must be between 1 and 5");
        }

        Property property = propertyService.getPropertyById(propertyId);

        if (reviewRepository.findByPropertyAndReviewer(property, reviewer).isPresent()) {
            throw ApiException.badRequest("You have already reviewed this property");
        }

        Review review = new Review();
        review.setProperty(property);
        review.setReviewer(reviewer);
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now());

        return reviewRepository.save(review);
    }

    public Map<String, Object> getPropertyReviews(Long propertyId) {
        Property property = propertyService.getPropertyById(propertyId);
        List<Review> reviews = reviewRepository.findByPropertyOrderByCreatedAtDesc(property);
        Double avgRating = reviewRepository.findAverageRatingByProperty(property);
        Long count = reviewRepository.countByProperty(property);

        Map<String, Object> response = new HashMap<>();
        response.put("reviews", reviews);
        response.put("averageRating", avgRating);
        response.put("reviewCount", count);
        return response;
    }
}
