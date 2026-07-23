package com.roost.service;

import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.PropertyRepository;
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

    public PropertyService(PropertyRepository propertyRepository, UserRepository userRepository) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
    }

    public List<Property> getAllProperties() {
        return propertyRepository.findAll();
    }

    public Property addProperty(Property property) {
        if (property.getListedAt() == null) {
            property.setListedAt(LocalDateTime.now());
        }
        if (property.getLastConfirmedAt() == null) {
            property.setLastConfirmedAt(LocalDateTime.now());
        }
        return propertyRepository.save(property);
    }

    public List<Property> getPropertiesByOwner(User owner) {
        return propertyRepository.findByOwner(owner);
    }

    public List<Property> getByLocation(String location) {
        return propertyRepository.findByLocationContainingIgnoreCase(location);
    }

    public List<Property> getAvailableProperties() {
        return propertyRepository.findByAvailableTrue();
    }

    public List<Property> getByType(String type) {
        return propertyRepository.findByType(type);
    }

    public List<Property> getByPriceRange(double minPrice, double maxPrice) {
        return propertyRepository.findByPriceBetween(minPrice, maxPrice);
    }

    public List<Property> getNearby(double lat, double lng, double radiusKm) {
        return propertyRepository.findNearby(lat, lng, radiusKm);
    }

    public List<Property> filter(String houseType, Double minPrice, Double maxPrice, Integer bedrooms,
                                 Boolean furnished, Boolean parking, Boolean wifi, Boolean water,
                                 Boolean security, Boolean verified) {
        return propertyRepository.filterProperties(houseType, minPrice, maxPrice, bedrooms,
                furnished, parking, wifi, water, security, verified);
    }

    public Property incrementViewCount(Long id) {
        Property property = getPropertyById(id);
        property.setViewCount(property.getViewCount() + 1);
        return propertyRepository.save(property);
    }

    public Property confirmAvailability(Long id) {
        Property property = getPropertyById(id);
        property.setAvailable(true);
        property.setLastConfirmedAt(LocalDateTime.now());
        return propertyRepository.save(property);
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
        return propertyRepository.findByIdIn(user.getSavedPropertyIds());
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
        return propertyRepository.save(existing);
    }

    public Property getPropertyById(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));
    }
}
