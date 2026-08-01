package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.model.PropertyReport;
import com.roost.model.User;
import com.roost.repository.PropertyRepository;
import com.roost.repository.PropertyReportRepository;
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
    private final PropertyReportRepository propertyReportRepository;

    /** Three distinct people flagging the same listing is enough to pull
     *  it from public view pending an admin look, rather than waiting on
     *  someone to notice a report queue manually. */
    private static final int REPORT_THRESHOLD = 3;

    public PropertyService(PropertyRepository propertyRepository, UserRepository userRepository,
                            ReviewRepository reviewRepository, PropertyReportRepository propertyReportRepository) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.propertyReportRepository = propertyReportRepository;
    }

    /**
     * Populates the (not persisted) averageRating/reviewCount fields on a
     * property before it's returned to a caller. Every public method below
     * that hands back a Property or List<Property> to the controller routes
     * through this, so callers never need to remember to call it themselves.
     * Deliberately NOT applied in getPendingPhotoReview/approvePhotos (the
     * admin photo-review queue never populated ratings before this refactor
     * either) or in the internal getPropertyById used for ownership checks.
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
        return populateRatings(propertyRepository.findByStatus("PUBLISHED"));
    }

    public Property addProperty(Property property) {
        if (property.getStatus() == null || property.getStatus().isBlank()) {
            property.setStatus("PUBLISHED");
        }
        if ("PUBLISHED".equals(property.getStatus())) {
            assertCanPublish(property.getOwner());
        }
        if (property.getListedAt() == null) {
            property.setListedAt(LocalDateTime.now());
        }
        if (property.getLastConfirmedAt() == null) {
            property.setLastConfirmedAt(LocalDateTime.now());
        }
        recomputeVerification(property);
        return populateRatings(propertyRepository.save(property));
    }

    /**
     * Server-side enforcement that a listing can only go live once its
     * owner has a verified phone -- mirrors the client-side gate in the
     * Flutter wizard (add_property_page.dart), but doesn't rely on it.
     * Same reasoning as removing `role` from signup requests earlier:
     * a client-side check alone can always be bypassed by calling the
     * API directly, so the rule has to also live here.
     */
    private void assertCanPublish(User owner) {
        if (owner == null || !owner.isPhoneVerified()) {
            throw ApiException.badRequest("Verify your phone number before publishing a listing.");
        }
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
        return populateRatings(propertyRepository.findByOwner(owner));
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
        property.setRemindedAt(null);
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
        return populateRatings(propertyRepository.save(existing));
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
        if (updated.getStatus() != null && !updated.getStatus().equals(existing.getStatus())
                && "PUBLISHED".equals(updated.getStatus())) {
            assertCanPublish(existing.getOwner());
        }
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }
        existing.setLastConfirmedAt(LocalDateTime.now());
        recomputeVerification(existing);
        return populateRatings(propertyRepository.save(existing));
    }

    /**
     * Dedicated one-tap publish action for a draft, used by the
     * landlord dashboard's quick-publish button so flipping a draft
     * live doesn't require reopening the whole listing wizard. Still
     * runs the same phone-verification check as every other path to
     * PUBLISHED.
     */
    public Property publishDraft(Long id) {
        Property existing = getPropertyById(id);
        assertCanPublish(existing.getOwner());
        existing.setStatus("PUBLISHED");
        if (existing.getListedAt() == null) {
            existing.setListedAt(LocalDateTime.now());
        }
        existing.setLastConfirmedAt(LocalDateTime.now());
        recomputeVerification(existing);
        return populateRatings(propertyRepository.save(existing));
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

    /**
     * Records a report against a listing. One report per user per
     * listing -- repeat reports from the same person don't count twice
     * toward the auto-hide threshold, since that would let one person
     * hide a listing alone by spamming the button. Once REPORT_THRESHOLD
     * distinct users have reported it, the listing flips to UNDER_REVIEW
     * and disappears from public feed/search/detail immediately -- the
     * same status field and query filters built for drafts, reused here
     * rather than adding a second hidden-listing mechanism.
     */
    public PropertyReport reportProperty(Long propertyId, User reporter, String reason, String details) {
        Property property = getPropertyById(propertyId);

        if (propertyReportRepository.existsByPropertyAndReportedBy(property, reporter)) {
            throw ApiException.badRequest("You've already reported this listing.");
        }

        PropertyReport report = new PropertyReport();
        report.setProperty(property);
        report.setReportedBy(reporter);
        report.setReason(reason);
        report.setDetails(details);
        propertyReportRepository.save(report);

        long totalReports = propertyReportRepository.countByProperty(property);
        if (totalReports >= REPORT_THRESHOLD && "PUBLISHED".equals(property.getStatus())) {
            property.setStatus("UNDER_REVIEW");
            propertyRepository.save(property);
        }

        return report;
    }

    /** Listings currently hidden pending admin review after crossing the
     *  report threshold. */
    public List<Property> getFlaggedForReview() {
        return propertyRepository.findByStatus("UNDER_REVIEW");
    }

    public List<PropertyReport> getReportsForProperty(Long propertyId) {
        Property property = getPropertyById(propertyId);
        return propertyReportRepository.findByPropertyOrderByCreatedAtDesc(property);
    }

    /**
     * Admin decision after reviewing a flagged listing: either restore
     * it to public view (reports were unfounded) or leave it hidden.
     * Doesn't delete anything either way -- a genuinely bad listing
     * still gets removed through the existing delete endpoint, which
     * is a separate, more deliberate action than a review decision.
     */
    public Property resolveReportedListing(Long propertyId, boolean restore) {
        Property property = getPropertyById(propertyId);
        if (restore) {
            property.setStatus("PUBLISHED");
            return propertyRepository.save(property);
        }
        return property;
    }

    /**
     * Fetches a property without rating population -- used internally by
     * other methods in this class (incrementViewCount, updateProperty, etc.)
     * that are about to mutate and re-save it anyway, where populating
     * ratings on the intermediate read would just be wasted work, and by
     * other services (ApplicationService, ReviewService) and ownership
     * checks that only need the entity itself, not its rating display data.
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
