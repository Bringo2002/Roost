package com.roost.controller;

import com.roost.dto.ListingEventBatchRequest;
import com.roost.dto.ListingEventBatchResponse;
import com.roost.exception.ApiException;
import com.roost.model.User;
import com.roost.service.ListingEventService;
import com.roost.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * Receives batched listing interactions from the client. Requires a
 * signed-in user (covered by the {@code anyRequest().authenticated()} rule
 * in SecurityConfig): every event is attributable, and an anonymous write
 * endpoint that feeds ranking would be trivially spammable.
 */
@RestController
@RequestMapping("/api/events")
public class ListingEventController {

    /** Requests allowed per user in {@link #WINDOW}; batches carry up to 50 events each. */
    static final int MAX_REQUESTS = 60;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final ListingEventService listingEventService;
    private final RateLimiterService rateLimiterService;

    public ListingEventController(ListingEventService listingEventService,
                                  RateLimiterService rateLimiterService) {
        this.listingEventService = listingEventService;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Records a batch of listing interactions for the authenticated user.
     *
     * @return 200 with accepted/dropped counts, 401 without a user,
     *         400 for an invalid batch, 429 when the per-user rate limit is hit
     */
    @PostMapping("/listings")
    public ResponseEntity<?> recordListingEvents(
            @Valid @RequestBody ListingEventBatchRequest request,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        String key = "listing-events:user:" + user.getId();
        if (rateLimiterService.isBlocked(key, MAX_REQUESTS, WINDOW)) {
            throw ApiException.tooManyRequests("Too many event reports. Please slow down.");
        }
        rateLimiterService.record(key);
        ListingEventBatchResponse response = listingEventService.record(user, request.events());
        return ResponseEntity.ok(response);
    }
}
