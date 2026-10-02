package com.roost.controller;

import com.roost.dto.PropertyResponseDto;
import com.roost.dto.ReportRequestDto;
import com.roost.dto.ReportSubmissionResponseDto;
import com.roost.exception.ApiException;
import com.roost.model.MediaHash;
import com.roost.model.Property;
import com.roost.model.PropertyReport;
import com.roost.model.User;
import com.roost.model.Role;
import com.roost.repository.MediaHashRepository;
import com.roost.service.PropertyService;
import com.roost.service.PropertyRiskService;
import com.roost.service.RentEstimateService;
import com.roost.service.RateLimiterService;
import com.roost.service.R2StorageService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private static final Logger log = Logger.getLogger(PropertyController.class.getName());

    private final PropertyService propertyService;
    private final PropertyRiskService propertyRiskService;
    private final RentEstimateService rentEstimateService;
    private final RateLimiterService rateLimiterService;

    @org.springframework.beans.factory.annotation.Autowired
    private R2StorageService r2StorageService;

    @org.springframework.beans.factory.annotation.Autowired
    private MediaHashRepository mediaHashRepository;

    /** Safety net for the legacy {@code /upload-photo} endpoint. New uploads
     *  use {@code /presign-upload} and bypass this server entirely — this cap
     *  only applies to the deprecated base64 path. */
    private static final int MAX_PHOTO_BYTES = 8 * 1024 * 1024; // 8 MB

    /** Raised from 60 MB to 500 MB for backward compat with any client still
     *  using the legacy {@code /upload-video} endpoint. The presigned upload
     *  path has no server-side size limit since bytes never touch this server. */
    private static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024; // 500 MB

    public PropertyController(PropertyService propertyService, PropertyRiskService propertyRiskService,
                               RentEstimateService rentEstimateService, RateLimiterService rateLimiterService) {
        this.propertyService = propertyService;
        this.propertyRiskService = propertyRiskService;
        this.rentEstimateService = rentEstimateService;
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping
    public List<PropertyResponseDto> getAllProperties(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // Pagination is opt-in here too, same as /filter: omitting
        // page/size preserves the exact previous behaviour (return
        // everything PUBLISHED, unbounded) so existing/older clients
        // aren't silently truncated to one page.
        if (size == null) {
            return PropertyResponseDto.from(propertyService.getAllProperties());
        }
        int cappedSize = Math.min(Math.max(size, 1), 50);
        int safePage = page != null ? Math.max(page, 0) : 0;
        Pageable pageable = PageRequest.of(safePage, cappedSize);
        return PropertyResponseDto.from(propertyService.getAllProperties(lat, lng, pageable));
    }

    @GetMapping("/nearby")
    public List<PropertyResponseDto> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") double radius) {
        return PropertyResponseDto.from(propertyService.getNearby(lat, lng, radius));
    }

    /**
     * Public host-profile listings: PUBLISHED-only, scoped to one owner,
     * paginated by default (not opt-in like /filter or the root endpoint
     * above) -- there's no legacy client relying on an unbounded response
     * here, since this is a brand-new endpoint.
     */
    @GetMapping("/by-owner/{ownerId}")
    public List<PropertyResponseDto> getPublishedPropertiesByOwner(
            @PathVariable Long ownerId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int cappedSize = Math.min(Math.max(size != null ? size : 20, 1), 50);
        int safePage = page != null ? Math.max(page, 0) : 0;
        Pageable pageable = PageRequest.of(safePage, cappedSize);
        return PropertyResponseDto.from(propertyService.getPublishedPropertiesByOwner(ownerId, pageable));
    }

    @GetMapping("/filter")
    public List<PropertyResponseDto> filterProperties(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(required = false) Boolean furnished,
            @RequestParam(required = false) Boolean parking,
            @RequestParam(required = false) Boolean wifi,
            @RequestParam(required = false) Boolean water,
            @RequestParam(required = false) Boolean security,
            @RequestParam(required = false) Boolean verified,
            // Free-text remainder of the user's search box -- see
            // PropertyService#filter and PropertyRepository for how
            // this is matched. Only affects the paginated (size != null)
            // branch below: the unpaginated branch is unbounded/legacy
            // and untouched by this.
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // Pagination is opt-in: omitting page/size preserves the exact
        // previous behaviour (return everything matching, unbounded) so
        // existing/older clients aren't silently truncated to one page.
        if (size == null) {
            return PropertyResponseDto.from(propertyService.filter(type, minPrice, maxPrice, bedrooms, furnished, parking, wifi, water, security, verified));
        }
        int cappedSize = Math.min(Math.max(size, 1), 50);
        int safePage = page != null ? Math.max(page, 0) : 0;
        Pageable pageable = PageRequest.of(safePage, cappedSize);
        return PropertyResponseDto.from(propertyService.filter(type, minPrice, maxPrice, bedrooms, furnished, parking, wifi, water, security, verified, q, lat, lng, pageable));
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<PropertyResponseDto> incrementView(@PathVariable Long id) {
        return ResponseEntity.ok(PropertyResponseDto.from(propertyService.incrementViewCount(id)));
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
        return ResponseEntity.ok(PropertyResponseDto.forOwner(propertyService.confirmAvailability(id)));
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
        return ResponseEntity.ok(PropertyResponseDto.forOwner(propertyService.verifyGpsLocation(id, lat, lng)));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<?> reportProperty(
            @PathVariable Long id,
            @Valid @RequestBody ReportRequestDto body,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String rateLimitKey = "report:user:" + user.getId();
        if (rateLimiterService.isBlocked(rateLimitKey, 5, Duration.ofHours(1))) {
            throw ApiException.badRequest("Too many reports submitted. Please wait before submitting another report.");
        }

        PropertyReport report = propertyService.reportProperty(id, user, body.getReason(), body.getDetails());
        rateLimiterService.record(rateLimitKey);

        return ResponseEntity.ok(ReportSubmissionResponseDto.from(report, "Report received. Our team will review this listing."));
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
     * Server-side market-price estimate -- see RentEstimateService for
     * why this replaced a client-side computation that fetched up to
     * 50 full property records per detail-page view. No auth required:
     * this is the same kind of market data anyone viewing the listing
     * already sees, not anything sensitive.
     */
    @GetMapping("/{id}/rent-estimate")
    public ResponseEntity<?> getRentEstimate(@PathVariable Long id) {
        Property property = propertyService.getPropertyById(id);
        RentEstimateService.RentEstimate estimate = rentEstimateService.estimate(property);
        if (estimate == null) {
            return ResponseEntity.ok(Map.of("available", false));
        }
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("available", true);
        response.put("minPrice", estimate.minPrice());
        response.put("medianPrice", estimate.medianPrice());
        response.put("maxPrice", estimate.maxPrice());
        response.put("rating", estimate.rating());
        response.put("percentile", estimate.percentile());
        response.put("comparableCount", estimate.comparableCount());
        response.put("valueDrivers", estimate.valueDrivers());
        return ResponseEntity.ok(response);
    }

    /**
     * Listings similar to this one -- same house type and bedroom
     * count, nearby -- for the property detail page's "similar
     * listings" carousel. No auth required, same reasoning as
     * rent-estimate above: this is public browse data, not anything
     * tied to the viewer.
     */
    @GetMapping("/{id}/similar")
    public List<PropertyResponseDto> getSimilarProperties(@PathVariable Long id) {
        return PropertyResponseDto.from(propertyService.getSimilarProperties(id));
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
        return ResponseEntity.ok(PropertyResponseDto.from(propertyService.getSavedProperties(user)));
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
        return ResponseEntity.ok(PropertyResponseDto.forOwner(propertyService.addProperty(property)));
    }

    // ── Presigned direct-to-R2 uploads ──────────────────────────────────────

    /**
     * Issues a short-lived presigned PUT URL so the client can upload photos
     * and videos directly to Cloudflare R2, bypassing this server's memory
     * and bandwidth limits. The server is not involved in the actual file
     * transfer — it only validates auth, generates a signed URL, and returns
     * the URL pair (upload target + permanent public URL).
     *
     * <p>Request body:
     * <pre>{@code
     *   {
     *     "type": "photo" | "video",
     *     "contentHash": "<optional SHA-256 hex of file bytes>"
     *   }
     * }</pre>
     *
     * <p>If {@code contentHash} is provided and matches an existing upload,
     * the response short-circuits with {@code {"existing": true, "publicUrl": "..."}}.
     *
     * <p>Response (new upload):
     * <pre>{@code
     *   {
     *     "uploadUrl": "<presigned PUT URL, valid for 15-60 min>",
     *     "publicUrl": "<permanent public URL, readable after PUT>",
     *     "key":       "<R2 object key, e.g. properties/uuid.jpg>"
     *   }
     * }</pre>
     *
     * @param payload JSON body containing {@code type} ("photo" or "video").
     * @param user    Must be authenticated with {@link Role#LANDLORD}.
     */
    @PostMapping("/presign-upload")
    public ResponseEntity<?> presignUpload(
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Only landlords can upload property media."));
        }

        String type = payload.get("type");
        if (type == null || (!"photo".equals(type) && !"video".equals(type))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "type must be \"photo\" or \"video\""));
        }

        // Photo: JPEG, short TTL since photos are small.
        // Video: MP4, long TTL since videos can be hundreds of MB.
        String contentType;
        String extension;
        int ttlMinutes;
        if ("photo".equals(type)) {
            contentType = "image/jpeg";
            extension = ".jpg";
            ttlMinutes = 15;
        } else {
            contentType = "video/mp4";
            extension = ".mp4";
            ttlMinutes = 60;
        }

        // ── Dedup check: short-circuit if the same content was already uploaded ──
        String contentHash = payload.get("contentHash");
        if (contentHash != null && !contentHash.isBlank()) {
            Optional<MediaHash> existing = mediaHashRepository.findByContentHash(contentHash);
            if (existing.isPresent()) {
                log.info(String.format("Dedup hit for user %s: hash=%s url=%s",
                        user.getUsername(), contentHash, existing.get().getPublicUrl()));
                return ResponseEntity.ok(Map.of(
                        "existing", true,
                        "publicUrl", existing.get().getPublicUrl()
                ));
            }
        }

        try {
            R2StorageService.PresignedUpload presign =
                    r2StorageService.generatePresignedPut("properties/", contentType, extension, ttlMinutes);

            log.info(String.format("Presigned %s upload for user %s: key=%s ttl=%dm",
                    type, user.getUsername(), presign.key(), ttlMinutes));

            Map<String, Object> response = new HashMap<>();
            response.put("existing", false);
            response.put("uploadUrl", presign.uploadUrl());
            response.put("publicUrl", presign.publicUrl());
            response.put("key",       presign.key());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.warning("Presigned upload failed: " + e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Media uploads are unavailable right now. Please try again."));
        }
    }

    /**
     * Records a completed direct upload in the content-hash index so future
     * identical files can be short-circuited at the presign step.
     *
     * <p>Called by the client after a successful PUT to R2.
     *
     * <p>Request body:
     * <pre>{@code
     *   {
     *     "contentHash": "<SHA-256 hex>",
     *     "publicUrl":   "<permanent R2 public URL>",
     *     "key":         "<R2 object key>",
     *     "contentType": "image/jpeg" | "video/mp4" | ...,
     *     "sizeBytes":   "<file size in bytes>"
     *   }
     * }</pre>
     */
    @PostMapping("/confirm-upload")
    public ResponseEntity<?> confirmUpload(
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (user.getRole() != Role.LANDLORD) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Only landlords can confirm uploads."));
        }

        String contentHash = payload.get("contentHash");
        String publicUrl   = payload.get("publicUrl");
        String key         = payload.get("key");
        String contentType = payload.get("contentType");
        String sizeBytesStr = payload.get("sizeBytes");

        if (contentHash == null || contentHash.isBlank() ||
            publicUrl == null || publicUrl.isBlank() ||
            key == null || key.isBlank() ||
            contentType == null || contentType.isBlank() ||
            sizeBytesStr == null || sizeBytesStr.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "All fields required: contentHash, publicUrl, key, contentType, sizeBytes"));
        }

        long sizeBytes;
        try {
            sizeBytes = Long.parseLong(sizeBytesStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "sizeBytes must be a number"));
        }

        // Idempotent: if the hash already exists, skip the insert.
        if (mediaHashRepository.findByContentHash(contentHash).isEmpty()) {
            MediaHash hash = new MediaHash(contentHash, publicUrl, key, contentType, sizeBytes);
            mediaHashRepository.save(hash);
            log.info(String.format("Recorded dedup hash for user %s: hash=%s key=%s size=%d",
                    user.getUsername(), contentHash, key, sizeBytes));
        }

        return ResponseEntity.ok(Map.of("recorded", true));
    }

    // ── Legacy upload endpoints (deprecated — use /presign-upload) ─────────

    /**
     * @deprecated Use {@code POST /api/properties/presign-upload} instead.
     *             This endpoint routes photo bytes through the application server
     *             via base64 JSON, limiting throughput and memory. Retained for
     *             backward compatibility with older clients.
     */
    @Deprecated
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
     * @deprecated Use {@code POST /api/properties/presign-upload} with
     *             {@code type: "video"} instead. This endpoint routes the
     *             entire video through the application server as base64,
     *             consuming server memory and bandwidth unnecessarily.
     *             Retained for backward compatibility with older clients.
     */
    @Deprecated
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
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Video is too large (max 500MB). Consider using /presign-upload for large uploads."));
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
        return ResponseEntity.ok(PropertyResponseDto.forOwner(propertyService.getPropertiesByOwner(user)));
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
        return ResponseEntity.ok(PropertyResponseDto.forOwner(propertyService.updateProperty(id, property)));
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
        return ResponseEntity.ok(PropertyResponseDto.forOwner(propertyService.setAvailability(id, available)));
    }

    @GetMapping("/{id}")
    public PropertyResponseDto getPropertyById(@PathVariable Long id, @AuthenticationPrincipal User user) {
        Property property = propertyService.getPropertyDetail(id);
        boolean isOwner = user != null && property.getOwner() != null && property.getOwner().getId().equals(user.getId());
        // Any non-PUBLISHED status (DRAFT, or UNDER_REVIEW after crossing
        // the report threshold) is only visible to its owner -- a
        // flagged listing shouldn't still be reachable by direct link
        // just because it's hidden from search rather than deleted.
        if (!"PUBLISHED".equals(property.getStatus()) && !isOwner) {
            throw com.roost.exception.ApiException.notFound("Property not found");
        }
        PropertyRiskService.PriceComparison comparison = propertyRiskService.getPriceComparison(property);
        return PropertyResponseDto.forViewer(property, comparison, user);
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
        return ResponseEntity.ok(PropertyResponseDto.forOwner(propertyService.publishDraft(id)));
    }

    /**
     * Public (no auth) -- the endorsement token is a shareable link the
     * caretaker/agent sends to the property owner so they can review
     * and endorse the listing without needing a Roost account.
     */
    @GetMapping("/endorse/{token}")
    public ResponseEntity<?> getEndorsementListing(@PathVariable String token) {
        return ResponseEntity.ok(PropertyResponseDto.forOwner(propertyService.getByEndorsementToken(token)));
    }

    /**
     * The property owner confirms (endorses) a listing created by a
     * caretaker/agent on their behalf. No auth -- the token itself
     * serves as a proof-of-possession credential.
     */
    @PostMapping("/endorse/{token}")
    public ResponseEntity<?> endorseListing(@PathVariable String token, @RequestBody Map<String, String> body) {
        String verifierName = body.get("verifierName");
        String verifierPhone = body.get("verifierPhone");
        if (verifierName == null || verifierName.isBlank() || verifierPhone == null || verifierPhone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "verifierName and verifierPhone are required"));
        }
        return ResponseEntity.ok(PropertyResponseDto.forOwner(
                propertyService.endorseListing(token, verifierName, verifierPhone)));
    }
}
