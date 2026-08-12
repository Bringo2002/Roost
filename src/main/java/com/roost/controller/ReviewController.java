package com.roost.controller;

import com.roost.dto.ReviewResponseDto;
import com.roost.model.User;
import com.roost.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
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
    public ResponseEntity<ReviewResponseDto> submitReview(@AuthenticationPrincipal User user,
                                                 @RequestBody Map<String, Object> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ReviewResponseDto.from(reviewService.submitReview(user, payload)));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<Map<String, Object>> getPropertyReviews(@PathVariable Long propertyId) {
        // getPropertyReviews has no authentication check -- this endpoint
        // is fully public, so raw entities here (as it returned before)
        // were the most exposed leak in the sweep: reviewer's full User
        // (email included) served to anyone, logged in or not.
        Map<String, Object> raw = reviewService.getPropertyReviews(propertyId);
        Map<String, Object> response = new HashMap<>(raw);
        @SuppressWarnings("unchecked")
        List<com.roost.model.Review> reviews = (List<com.roost.model.Review>) raw.get("reviews");
        response.put("reviews", reviews.stream().map(ReviewResponseDto::new).toList());
        return ResponseEntity.ok(response);
    }
}
