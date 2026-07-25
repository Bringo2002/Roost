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

@RestController
@RequestMapping("/api/properties")
@CrossOrigin(origins = "*")
public class PropertyController {

    private final PropertyService propertyService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.roost.repository.ReviewRepository reviewRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private R2StorageService r2StorageService;

    /** Reject anything absurdly large before it ever reaches R2 -- the
     *  app should be compressing photos client-side, so a well-behaved
     *  upload should never get near this. */
    private static final int MAX_PHOTO_BYTES = 8 * 1024 * 1024; // 8MB

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    private Property populateRatings(Property property) {
        if (property != null) {
            Double avg = reviewRepository.findAverageRatingByProperty(property);
            Long count = reviewRepository.countByProperty(property);
            property.setAverageRating(avg != null ? avg : 0.0);
            property.setReviewCount(count != null ? count : 0L);
        }
        return property;
    }

    private List<Property> populateRatings(List<Property> properties) {
        if (properties != null) {
            for (Property p : properties) {
                populateRatings(p);
            }
        }
        return properties;
    }

    @GetMapping
    public List<Property> getAllProperties() {
        return populateRatings(propertyService.getAllProperties());
    }

    @GetMapping("/nearby")
    public List<Property> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") double radius) {
        return populateRatings(propertyService.getNearby(lat, lng, radius));
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
        return populateRatings(propertyService.filter(type, minPrice, maxPrice, bedrooms, furnished, parking, wifi, water, security, verified));
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Property> incrementView(@PathVariable Long id) {
        return ResponseEntity.ok(populateRatings(propertyService.incrementViewCount(id)));
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
        return ResponseEntity.ok(populateRatings(propertyService.confirmAvailability(id)));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<?> reportProperty(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, @AuthenticationPrincipal User user) {
        String reason = body != null && body.containsKey("reason") ? body.get("reason") : "Unspecified issue";
        return ResponseEntity.ok(Map.of("message", "Report received for property " + id + ". Reason: " + reason));
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
        return ResponseEntity.ok(populateRatings(propertyService.getSavedProperties(user)));
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
        return ResponseEntity.ok(populateRatings(propertyService.addProperty(property)));
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
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
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
        return ResponseEntity.ok(populateRatings(propertyService.getPropertiesByOwner(user)));
    }

    @GetMapping("/hello")
    public String hello() {
        return "Roost API is LIVE";
    }

    @GetMapping("/location/{location}")
    public List<Property> getByLocation(@PathVariable String location) {
        return populateRatings(propertyService.getByLocation(location));
    }

    @GetMapping("/available")
    public List<Property> getAvailable() {
        return populateRatings(propertyService.getAvailableProperties());
    }

    @GetMapping("/type/{type}")
    public List<Property> getByType(@PathVariable String type) {
        return populateRatings(propertyService.getByType(type));
    }

    @GetMapping("/price")
    public List<Property> getByPriceRange(
            @RequestParam double min,
            @RequestParam double max) {
        return populateRatings(propertyService.getByPriceRange(min, max));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProperty(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Property property = propertyService.getPropertyById(id);
        if (user.getRole() != Role.LANDLORD || property.getOwner() == null || !property.getOwner().getId().equals(user.getId())) {
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
        return ResponseEntity.ok(populateRatings(propertyService.updateProperty(id, property)));
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
        return ResponseEntity.ok(populateRatings(propertyService.setAvailability(id, available)));
    }

    @GetMapping("/{id}")
    public Property getPropertyById(@PathVariable Long id) {
        return populateRatings(propertyService.getPropertyById(id));
    }
}