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
        recomputeVerification(property);
        return propertyRepository.save(property);
    }

    /**
     * Recomputes the "Verified" badge from the three v1 signals: owner's
     * phone verified, GPS location confirmed, photos admin-approved.
     * Called whenever any underlying signal could have changed -- never
     * set verified directly from client input.
     */
    private void recomputeVerification(Property property) {
        boolean phoneVerified = property.getOwner() != null && property.getOwner().isPhoneVerified();
        boolean gpsConfirmed = property.getLatitude() != null && property.getLongitude() != null;
        property.setVerified(phoneVerified && gpsConfirmed && property.isPhotoApproved());
    }

    /** Re-checks verification for every listing owned by [owner] -- called
     *  when their phone verification status changes, since that's a
     *  signal that lives outside any single property update. */
    public void recomputeVerificationForOwner(User owner) {
        List<Property> properties = propertyRepository.findByOwner(owner);
        for (Property p : properties) {
            recomputeVerification(p);
        }
        propertyRepository.saveAll(properties);
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
        property.setRemindedAt(null);
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

    /**
     * Flips availability only -- deliberately avoids touching any other
     * field. See PropertyController.setAvailability for why: updateProperty
     * above does a full overwrite, which is only safe when the caller has
     * the complete, current object (e.g. the edit-listing flow), not for
     * a simple toggle from a listings dashboard.
     */
    public Property setAvailability(Long id, boolean available) {
        Property existing = getPropertyById(id);
        existing.setAvailable(available);
        existing.setLastConfirmedAt(LocalDateTime.now());
        existing.setRemindedAt(null);
        return propertyRepository.save(existing);
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
        recomputeVerification(existing);
        return propertyRepository.save(existing);
    }

    /** Admin marks a listing's photos as reviewed and genuine. */
    public Property approvePhotos(Long id) {
        Property property = getPropertyById(id);
        property.setPhotoApproved(true);
        recomputeVerification(property);
        return propertyRepository.save(property);
    }

    /** Listings awaiting photo review -- have GPS + a confirmed phone
     *  already, just missing the admin sign-off. */
    public List<Property> getPendingPhotoReview() {
        return propertyRepository.findByPhotoApprovedFalse();
    }

    public Property getPropertyById(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));
    }
}
