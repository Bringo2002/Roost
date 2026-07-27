package com.roost.service;

import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.PropertyRepository;
import com.roost.repository.ReviewRepository;
import com.roost.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    public PropertyService(PropertyRepository propertyRepository, UserRepository userRepository,
                            ReviewRepository reviewRepository) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
    }

    /**
     * Populates the (not persisted) averageRating/reviewCount fields on a
     * property before it's returned to a caller. Every public method below
     * that hands back a Property or List<Property> routes through this, so
     * callers (the controller) never need to remember to call it themselves.
     */
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

    public List<Property> getAllProperties() {
        return populateRatings(propertyRepository.findAll());
    }

    public Property addProperty(Property property) {
        if (property.getListedAt() == null) {
            property.setListedAt(LocalDateTime.now());
        }
        if (property.getLastConfirmedAt() == null) {
            property.setLastConfirmedAt(LocalDateTime.now());
        }
        return populateRatings(propertyRepository.save(property));
    }

    public List<Property> getPropertiesByOwner(User owner) {
        return populateRatings(propertyRepository.findByOwner(owner));
    }

    public List<Property> getByLocation(String location) {
        return populateRatings(propertyRepository.findByLocationContainingIgnoreCase(location));
    }

    public List<Property> getAvailableProperties() {
        return populateRatings(propertyRepository.findByAvailableTrue());
    }

    public List<Property> getByType(String type) {
        return populateRatings(propertyRepository.findByType(type));
    }

    public List<Property> getByPriceRange(double minPrice, double maxPrice) {
        return populateRatings(propertyRepository.findByPriceBetween(minPrice, maxPrice));
    }

    public List<Property> getNearby(double lat, double lng, double radiusKm) {
        return populateRatings(propertyRepository.findNearby(lat, lng, radiusKm));
    }

    public List<Property> filter(String houseType, Double minPrice, Double maxPrice, Integer bedrooms,
                                 Boolean furnished, Boolean parking, Boolean wifi, Boolean water,
                                 Boolean security, Boolean verified) {
        return populateRatings(propertyRepository.filterProperties(houseType, minPrice, maxPrice, bedrooms,
                furnished, parking, wifi, water, security, verified));
    }

    public Property incrementViewCount(Long id) {
        Property property = getPropertyById(id);
        property.setViewCount(property.getViewCount() + 1);
        return populateRatings(propertyRepository.save(property));
    }

    public Property confirmAvailability(Long id) {
        Property property = getPropertyById(id);
        property.setAvailable(true);
        property.setLastConfirmedAt(LocalDateTime.now());
        return populateRatings(propertyRepository.save(property));
    }

    public void saveProperty(User user, Long propertyId) {
        Property property = getPropertyById(propertyId);
        if (!user.getSavedPropertyIds().contains(propertyId)) {
            user.getSavedPropertyIds().add(propertyId);
            property.setSaveCount(property.getSaveCount() + 1);
            userRepository.save(user);
            propertyRepository.save(property);
        }
    }

    public void unsaveProperty(User user, Long propertyId) {
        Property property = getPropertyById(propertyId);
        if (user.getSavedPropertyIds().contains(propertyId)) {
            user.getSavedPropertyIds().remove(propertyId);
            property.setSaveCount(Math.max(0, property.getSaveCount() - 1));
            userRepository.save(user);
            propertyRepository.save(property);
        }
    }

    public List<Property> getSavedProperties(User user) {
        if (user.getSavedPropertyIds() == null || user.getSavedPropertyIds().isEmpty()) {
            return Collections.emptyList();
        }
        return populateRatings(propertyRepository.findByIdIn(user.getSavedPropertyIds()));
    }

    public void deleteProperty(Long id) {
        propertyRepository.deleteById(id);
    }

    public Property updateProperty(Long id, Property updated) {
        Property existing = getPropertyById(id);
        existing.setTitle(updated.getTitle());
        existing.setLocation(updated.getLocation());
        existing.setPrice(updated.getPrice());
        existing.setBedrooms(updated.getBedrooms());
        existing.setType(updated.getType());
        existing.setAvailable(updated.isAvailable());
        existing.setLandlordPhone(updated.getLandlordPhone());
        existing.setLandlordName(updated.getLandlordName());
        existing.setDescription(updated.getDescription());
        existing.setImageUrl(updated.getImageUrl());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        existing.setHouseType(updated.getHouseType());
        existing.setBathrooms(updated.getBathrooms());
        existing.setFurnished(updated.isFurnished());
        existing.setParking(updated.isParking());
        existing.setWater(updated.isWater());
        existing.setWifi(updated.isWifi());
        existing.setSecurity(updated.isSecurity());
        existing.setPetFriendly(updated.isPetFriendly());
        existing.setBalcony(updated.isBalcony());
        existing.setDeposit(updated.getDeposit());
        existing.setMoveInDate(updated.getMoveInDate());
        existing.setImageUrls(updated.getImageUrls());
        existing.setVideoUrl(updated.getVideoUrl());
        if (updated.getCountry() != null) existing.setCountry(updated.getCountry());
        existing.setLastConfirmedAt(LocalDateTime.now());
        return populateRatings(propertyRepository.save(existing));
    }

    /**
     * Fetches a property without rating population -- used internally by
     * other methods in this class (incrementViewCount, updateProperty, etc.)
     * that are about to mutate and re-save it anyway, where populating
     * ratings on the intermediate read would just be wasted work, and by
     * other services (ApplicationService, ReviewService) that only need
     * the entity itself, not its rating display data.
     */
    public Property getPropertyById(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));
    }

    /** Same lookup as {@link #getPropertyById}, but with ratings populated -- for the property detail view specifically. */
    public Property getPropertyDetail(Long id) {
        return populateRatings(getPropertyById(id));
    }
}
