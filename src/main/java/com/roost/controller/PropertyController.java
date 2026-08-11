package com.roost.controller;

import com.roost.model.Property;
import com.roost.model.User;
import com.roost.model.Role;
import com.roost.service.PropertyService;
import com.roost.service.R2StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/properties")
@CrossOrigin(origins = "*")
public class PropertyController {

    private static final Logger log = Logger.getLogger(PropertyController.class.getName());

    private final PropertyService propertyService;

    @org.springframework.beans.factory.annotation.Autowired
    private R2StorageService r2StorageService;

    /** Reject anything absurdly large before it ever reaches R2 -- the
     *  app should be compressing photos client-side, so a well-behaved
     *  upload should never get near this. */
    private static final int MAX_PHOTO_BYTES = 8 * 1024 * 1024; // 8MB

    /** A short vertical walkthrough clip, not a feature film -- generous
     *  enough for a genuine 30-60s walkthrough at reasonable mobile
     *  bitrates, capped well short of anything that would strain R2
     *  storage costs or a landlord's mobile data uploading it. */
    private static final int MAX_VIDEO_BYTES = 60 * 1024 * 1024; // 60MB

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public List<Property> getAllProperties() {
        return propertyService.getAllProperties();
    }

    @GetMapping("/nearby")
    public List<Property> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") double radius) {
        return propertyService.getNearby(lat, lng, radius);
    }

    @GetMapping("/filter")
    public List<Property> filterProperties(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(required = false) Boolean furnished,
            @RequestParam(required = false) Boolean parking,
            @RequestParam(required = false) Boolean wifi,
            @RequestParam(required = false) Boolean water,
            @RequestParam(required = false) Boolean security,
            @RequestParam(required = false) Boolean verified) {
        return propertyService.filter(type, minPrice, maxPrice, bedrooms, furnished, parking, wifi, water, security, verified);
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Property> incrementView(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.incrementViewCount(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirmAvailability(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Property property = propertyService.getPropertyById(id);
        if (user.getRole() != Role.LANDLORD || property.getOwner() == null || !property.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only property owner can confirm availability."));
        }
        return ResponseEntity.ok(propertyService.confirmAvailability(id));
    }

    /**
     * Called when the owner is physically at the property and taps
     * "Verify Location" -- takes the device's current GPS reading and
     * compares it against the listing's pinned coordinates server-side
     * (PropertyService.verifyGpsLocation), rather than trusting a
     * client-reported "yes I'm here."
     */
    @PostMapping("/{id}/verify-gps")
    public ResponseEntity<?> verifyGpsLocation(@PathVariable Long id,
                                                 @RequestBody Map<String, Double> body,
                                                 @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Property property = propertyService.getPropertyById(id);
        if (user.getRole() != Role.LANDLORD || property.getOwner() == null || !property.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only property owner can verify this location."));
        }
        Double lat = body.get("latitude");
        Double lng = body.get("longitude");
        if (lat == null || lng == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "latitude and longitude are required"));
        }
        return ResponseEntity.ok(propertyService.verifyGpsLocation(id, lat, lng));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<?> reportProperty(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        String reason = body != null && body.containsKey("reason") ? body.get("reason") : "Unspecified issue";
        String details = body != null ? body.get("details") : null;
        propertyService.reportProperty(id, user, reason, details);
        return ResponseEntity.ok(Map.of("message", "Report received. Our team will review this listing."));
    }

    /** Lets the app decide whether to show the "confirm accuracy" prompt
     *  at all, before the user tries to submit one. */
    @GetMapping("/{id}/community-check/eligible")
    public ResponseEntity<?> canSubmitCommunityCheck(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        boolean eligible = propertyService.canSubmitCommunityCheck(id, user);
        return ResponseEntity.ok(Map.of("eligible", eligible));
    }

    /**
     * A tenant's post-application confirmation of whether this listing
     * matched what was advertised -- gated server-side on having
     * actually applied to it (PropertyService.submitCommunityCheck),
     * not just on the client claiming eligibility.
     */
    @PostMapping("/{id}/community-check")
    public ResponseEntity<?> submitCommunityCheck(@PathVariable Long id,
                                                    @RequestBody Map<String, Boolean> body,
                                                    @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        boolean visited = Boolean.TRUE.equals(body.get("visited"));
        boolean photosAccurate = Boolean.TRUE.equals(body.get("photosAccurate"));
        boolean locationAccurate = Boolean.TRUE.equals(body.get("locationAccurate"));
        boolean priceAccurate = Boolean.TRUE.equals(body.get("priceAccurate"));
        boolean wouldRecommend = Boolean.TRUE.equals(body.get("wouldRecommend"));
        propertyService.submitCommunityCheck(id, user, visited, photosAccurate, locationAccurate, priceAccurate, wouldRecommend);
        return ResponseEntity.ok(Map.of("message", "Thanks for helping keep Roost accurate."));
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<?> saveProperty(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        propertyService.saveProperty(user, id);
        return ResponseEntity.ok(Map.of("message", "Property saved to favorites"));
    }

    @DeleteMapping("/{id}/save")
    public ResponseEntity<?> unsaveProperty(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        propertyService.unsaveProperty(user, id);
        return ResponseEntity.ok(Map.of("message", "Property removed from favorites"));
    }

    @GetMapping("/saved")
    public ResponseEntity<?> getSavedProperties(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(propertyService.getSavedProperties(user));
    }

    @PostMapping
    public ResponseEntity<?> addProperty(@RequestBody Property property, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Only landlords can list properties."));
        }
        property.setOwner(user);
        property.setLandlordId(user.getId().toString());
        property.setLandlordName(user.getName());
        property.setLandlordPhone(user.getPhone() != null ? user.getPhone() : property.getLandlordPhone());
        return ResponseEntity.ok(propertyService.addProperty(property));
    }

    /**
     * Uploads a single property photo and returns its public URL. Photos
     * are plain public content (unlike E2EE chat attachments), so this
     * returns a directly-loadable URL rather than an opaque storage key.
     */
    @PostMapping("/upload-photo")
    public ResponseEntity<?> uploadPhoto(@RequestBody Map<String, String> payload, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Only landlords can upload property photos."));
        }
        String data = payload.get("data");
        if (data == null || data.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "data is required"));
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Photo data is not valid base64"));
        }
        if (bytes.length > MAX_PHOTO_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "Photo is too large (max 8MB)"));
        }

        try {
            String url = r2StorageService.uploadPublic(bytes);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalStateException e) {
            log.warning("Property photo upload failed: " + e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Photo uploads aren't available right now. Please try again shortly."
            ));
        }
    }

    /**
     * A single optional walkthrough video per listing, uploaded the same
     * way photos are (base64 JSON body) for consistency with the
     * existing upload flow, but through its own endpoint with a much
     * larger size cap and a real video content-type/extension so
     * players can rely on both instead of guessing from raw bytes.
     */
    @PostMapping("/upload-video")
    public ResponseEntity<?> uploadVideo(@RequestBody Map<String, String> payload, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Only landlords can upload property videos."));
        }
        String data = payload.get("data");
        if (data == null || data.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "data is required"));
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Video data is not valid base64"));
        }
        if (bytes.length > MAX_VIDEO_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "Video is too large (max 60MB)"));
        }

        try {
            String url = r2StorageService.uploadPublic(bytes, "video/mp4", ".mp4");
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalStateException e) {
            log.warning("Property video upload failed: " + e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Video uploads aren't available right now. Please try again shortly."
            ));
        }
    }

    @GetMapping("/my-listings")
    public ResponseEntity<?> getMyListings(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(propertyService.getPropertiesByOwner(user));
    }

    @GetMapping("/hello")
    public String hello() {
        return "Roost API is LIVE";
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProperty(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Property property = propertyService.getPropertyById(id);
        boolean isOwner = property.getOwner() != null && property.getOwner().getId().equals(user.getId())
                && user.getRole() == Role.LANDLORD;
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        propertyService.deleteProperty(id);
        return ResponseEntity.ok(Map.of("message", "Deleted successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProperty(@PathVariable Long id, @RequestBody Property property, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Property existing = propertyService.getPropertyById(id);
        if (user.getRole() != Role.LANDLORD || existing.getOwner() == null || !existing.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(propertyService.updateProperty(id, property));
    }

    /**
     * Toggles availability only. Deliberately separate from PUT /{id} --
     * that endpoint does a full field-by-field overwrite, so sending it
     * a client-reconstructed Property missing fields (house type,
     * amenities, deposit, etc.) silently wipes them. This is the only
     * safe way to flip "available" without knowing every other field.
     */
    @PatchMapping("/{id}/availability")
    public ResponseEntity<?> setAvailability(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Boolean available = payload.get("available");
        if (available == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "available is required"));
        }
        Property existing = propertyService.getPropertyById(id);
        if (user.getRole() != Role.LANDLORD || existing.getOwner() == null || !existing.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(propertyService.setAvailability(id, available));
    }

    @GetMapping("/{id}")
    public Property getPropertyById(@PathVariable Long id, @AuthenticationPrincipal User user) {
        Property property = propertyService.getPropertyDetail(id);
        boolean isOwner = user != null && property.getOwner() != null && property.getOwner().getId().equals(user.getId());
        // Any non-PUBLISHED status (DRAFT, or UNDER_REVIEW after crossing
        // the report threshold) is only visible to its owner -- a
        // flagged listing shouldn't still be reachable by direct link
        // just because it's hidden from search rather than deleted.
        if (!"PUBLISHED".equals(property.getStatus()) && !isOwner) {
            throw com.roost.exception.ApiException.notFound("Property not found");
        }
        return property;
    }

    /**
     * One-tap publish for a draft from the dashboard, without reopening
     * the full listing wizard. Still enforced server-side by
     * PropertyService.publishDraft -- the phone-verification check
     * can't be skipped just because this is a shortcut.
     */
    @PatchMapping("/{id}/publish")
    public ResponseEntity<?> publishDraft(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Property existing = propertyService.getPropertyById(id);
        if (user.getRole() != Role.LANDLORD || existing.getOwner() == null || !existing.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(propertyService.publishDraft(id));
    }
}
