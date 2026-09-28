package com.roost.controller;

import com.roost.dto.MyListingsPageResponse;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.PropertyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * v2 property endpoints. Lives beside (not inside) {@link PropertyController}
 * so the v1 contract stays byte-for-byte untouched while clients migrate --
 * v1 endpoints keep working for at least one release cycle per CLAUDE.md's
 * API versioning rule.
 *
 * Unlike GET /api/properties/**, nothing under /api/v2 is permitAll in
 * SecurityConfig: it falls through to anyRequest().authenticated().
 */
@RestController
@RequestMapping("/api/v2/properties")
public class PropertyV2Controller {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    private final PropertyService propertyService;

    public PropertyV2Controller(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /**
     * Paginated, slim-payload replacement for GET /api/properties/my-listings.
     *
     * Query params: {@code page} (zero-based, default 0), {@code size}
     * (default 20, clamped to 1..50), {@code filter} (ALL | PUBLISHED | DRAFT
     * | RENTED, default ALL; anything else is a 400).
     *
     * Each item is a {@link com.roost.dto.PropertyListItemDto}; fetch the full
     * record with GET /api/properties/{id} when a screen needs more.
     */
    @GetMapping("/my-listings")
    public ResponseEntity<?> getMyListings(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        PropertyService.MyListingsPage result =
                propertyService.getMyListingsPage(user, filter, safePage, safeSize);
        return ResponseEntity.ok(MyListingsPageResponse.from(result.listings(), result.counts()));
    }
}
