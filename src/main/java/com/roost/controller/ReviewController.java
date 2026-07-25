package com.roost.controller;

import com.roost.model.Review;
import com.roost.model.User;
import com.roost.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<Review> submitReview(@AuthenticationPrincipal User user,
                                                 @RequestBody Map<String, Object> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(reviewService.submitReview(user, payload));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<Map<String, Object>> getPropertyReviews(@PathVariable Long propertyId) {
        return ResponseEntity.ok(reviewService.getPropertyReviews(propertyId));
    }
}
