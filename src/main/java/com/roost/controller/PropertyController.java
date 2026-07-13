package com.roost.controller;


import com.roost.model.Property;
import com.roost.model.User;
import com.roost.model.Role;
import com.roost.service.PropertyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/properties")
@CrossOrigin(origins = "*")
public class PropertyController {

    private final PropertyService propertyService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.roost.repository.ReviewRepository reviewRepository;

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
        for (Property p : properties) {
            populateRatings(p);
        }
        return properties;
    }

    @GetMapping
    public List<Property> getAllProperties() {
        return populateRatings(propertyService.getAllProperties());
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
        return ResponseEntity.ok(populateRatings(propertyService.addProperty(property)));
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

    @GetMapping("/{id}")
    public Property getPropertyById(@PathVariable Long id) {
        return populateRatings(propertyService.getPropertyById(id));
    }
}