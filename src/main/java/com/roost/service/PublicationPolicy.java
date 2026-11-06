package com.roost.service;

import com.roost.exception.PublicationViolation;
import com.roost.model.Property;
import com.roost.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The single authoritative definition of what a listing must satisfy to be
 * PUBLISHED. Pure and stateless so every route that can make a listing public
 * (create, edit, dashboard publish) applies exactly the same rules, and so
 * the rules can be tested without a Spring context.
 *
 * <p>The client wizard validates the same fields for immediate feedback, but
 * only this policy decides: a client-side check can always be bypassed by
 * calling the API directly.
 */
public final class PublicationPolicy {

    /** Mirrors the wizard's minimum (add_property_page.dart, {@code _minPhotos}). */
    public static final int MIN_PHOTOS = 3;

    /** Same set as the wizard's property-type chips and SearchController. */
    private static final Set<String> HOUSE_TYPES =
            Set.of("BEDSITTER", "STUDIO", "1BR", "2BR", "3BR+");

    private PublicationPolicy() {
    }

    /**
     * Checks {@code property} against every publication requirement and
     * reports all failures at once, so a lister can fix them in one pass.
     *
     * @param property the listing as it would be once published
     * @return the violations in a stable order; empty when it may be published
     */
    public static List<PublicationViolation> validate(Property property) {
        List<PublicationViolation> violations = new ArrayList<>();

        if (isBlank(property.getTitle())) {
            add(violations, "title", "TITLE_REQUIRED", "Give your listing a title.");
        }
        double price = property.getPrice();
        if (!(price > 0) || Double.isInfinite(price)) {
            add(violations, "price", "PRICE_INVALID", "Enter a valid monthly rent greater than zero.");
        }
        if (isBlank(property.getLocation())) {
            add(violations, "location", "LOCATION_REQUIRED", "Add a neighborhood or street.");
        }
        validateCoordinates(property, violations);
        if (property.getHouseType() == null || !HOUSE_TYPES.contains(property.getHouseType())) {
            add(violations, "houseType", "HOUSE_TYPE_INVALID", "Choose a valid property type.");
        }
        if (countPhotos(property) < MIN_PHOTOS) {
            add(violations, "imageUrls", "PHOTOS_MIN", "Add at least " + MIN_PHOTOS + " photos.");
        }
        if (isBlank(property.getLandlordPhone())) {
            add(violations, "landlordPhone", "CONTACT_PHONE_REQUIRED", "Add a contact phone number.");
        }
        String role = property.getManagerRole();
        if (role != null && !"LANDLORD".equals(role)) {
            if (isBlank(property.getCaretakerName())) {
                add(violations, "caretakerName", "CARETAKER_NAME_REQUIRED",
                        "Enter the name of the on-site contact.");
            }
            if (isBlank(property.getCaretakerPhone())) {
                add(violations, "caretakerPhone", "CARETAKER_PHONE_REQUIRED",
                        "Enter the on-site contact's phone number.");
            }
        }
        User owner = property.getOwner();
        if (owner == null || !owner.isPhoneVerified()) {
            add(violations, "ownerPhone", "OWNER_PHONE_UNVERIFIED",
                    "Verify your phone number before publishing a listing.");
        }
        return violations;
    }

    private static void validateCoordinates(Property property, List<PublicationViolation> violations) {
        Double lat = property.getLatitude();
        Double lng = property.getLongitude();
        if (lat == null || lng == null) {
            add(violations, "coordinates", "COORDINATES_REQUIRED", "Confirm the property's location.");
        } else if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            add(violations, "coordinates", "COORDINATES_INVALID", "The confirmed location isn't valid.");
        }
    }

    private static int countPhotos(Property property) {
        List<String> urls = property.getImageUrls();
        if (urls == null) {
            return 0;
        }
        return (int) urls.stream().filter(u -> !isBlank(u)).count();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void add(List<PublicationViolation> violations, String field, String code, String message) {
        violations.add(new PublicationViolation(field, code, message));
    }
}
