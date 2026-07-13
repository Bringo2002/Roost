package com.roost.controller;

import com.roost.model.*;
import com.roost.repository.ReviewRepository;
import com.roost.service.PropertyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PropertyService propertyService;

    @PostMapping
    public ResponseEntity<?> submitReview(@AuthenticationPrincipal User user, @RequestBody Map<String, Object> payload) {
        if (user == null) return ResponseEntity.status(401).build();

        try {
            Long propertyId = Long.valueOf(payload.get("propertyId").toString());
            int rating = Integer.parseInt(payload.get("rating").toString());
            String comment = payload.get("comment") != null ? payload.get("comment").toString() : "";

            if (rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rating must be between 1 and 5"));
            }

            Property property = propertyService.getPropertyById(propertyId);

            // Check if user already reviewed this property
            if (reviewRepository.findByPropertyAndReviewer(property, user).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "You have already reviewed this property"));
            }

            Review review = new Review();
            review.setProperty(property);
            review.setReviewer(user);
            review.setRating(rating);
            review.setComment(comment);
            review.setCreatedAt(LocalDateTime.now());

            Review saved = reviewRepository.save(review);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<?> getPropertyReviews(@PathVariable Long propertyId) {
        try {
            Property property = propertyService.getPropertyById(propertyId);
            List<Review> reviews = reviewRepository.findByPropertyOrderByCreatedAtDesc(property);
            Double avgRating = reviewRepository.findAverageRatingByProperty(property);
            Long count = reviewRepository.countByProperty(property);

            Map<String, Object> response = new HashMap<>();
            response.put("reviews", reviews);
            response.put("averageRating", avgRating);
            response.put("reviewCount", count);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
