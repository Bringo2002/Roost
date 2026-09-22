package com.roost.controller;

import com.roost.exception.ApiException;
import com.roost.service.GeminiSearchIntentService;
import com.roost.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * AI-assisted search intent parsing, for queries the client's own
 * regex parser can't handle (price ranges beyond "under N", amenities
 * beyond "furnished", Swahili/Sheng-mixed phrasing). Public endpoint --
 * same accessibility as /api/properties/filter, since search itself
 * requires no authentication.
 *
 * The client is expected to call this only as a fallback -- when its
 * own local parse extracted nothing -- and only on explicit search
 * submission, not on every keystroke. See RoostSearchBar's onSubmitted
 * wiring on the Flutter side. This keeps the common case exactly as
 * fast and free as before; only genuinely ambiguous queries spend an
 * AI call, and only once the user has signaled they're done typing.
 *
 * Also rate-limited by IP (see RateLimiterService, the same one
 * AuthController uses) -- unlike most public endpoints, every call
 * here costs real money against Roost's Gemini quota, and this one has
 * no authentication to fall back on for identifying abusive callers.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Set<String> VALID_HOUSE_TYPES = Set.of("BEDSITTER", "STUDIO", "1BR", "2BR", "3BR+");

    private static final int MAX_REQUESTS = 20;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final GeminiSearchIntentService geminiSearchIntentService;
    private final RateLimiterService rateLimiterService;

    public SearchController(GeminiSearchIntentService geminiSearchIntentService,
                             RateLimiterService rateLimiterService) {
        this.geminiSearchIntentService = geminiSearchIntentService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/parse-intent")
    public ResponseEntity<?> parseIntent(@RequestBody Map<String, String> payload, HttpServletRequest httpRequest) {
        String ipKey = "search-intent:ip:" + clientIp(httpRequest);
        if (rateLimiterService.isBlocked(ipKey, MAX_REQUESTS, WINDOW)) {
            throw ApiException.tooManyRequests("Too many search requests. Please try again shortly.");
        }
        rateLimiterService.record(ipKey);

        String query = payload.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query is required"));
        }

        Map<String, Object> result = geminiSearchIntentService.parseIntent(query);
        if (result == null) {
            return ResponseEntity.ok(Map.of("available", false));
        }

        Object houseType = result.get("houseType");
        if (houseType instanceof String s && !VALID_HOUSE_TYPES.contains(s)) {
            result.put("houseType", null);
        }

        result.put("available", true);
        return ResponseEntity.ok(result);
    }

    /** Same X-Forwarded-For extraction AuthController uses -- Railway
     *  sits in front of this app as a proxy, so request.getRemoteAddr()
     *  would just return Railway's internal address, not the caller's. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
