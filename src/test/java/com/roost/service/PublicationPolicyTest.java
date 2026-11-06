package com.roost.service;

import com.roost.exception.PublicationViolation;
import com.roost.model.Property;
import com.roost.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rule-by-rule tests for {@link PublicationPolicy}; no Spring context needed. */
class PublicationPolicyTest {

    /** A listing that satisfies every requirement. */
    private static Property validListing() {
        User owner = new User();
        owner.setId(1L);
        owner.setPhoneVerified(true);

        Property p = new Property();
        p.setOwner(owner);
        p.setTitle("Sunny 1BR in Kilimani");
        p.setPrice(25000);
        p.setLocation("Kilimani");
        p.setLatitude(-1.2921);
        p.setLongitude(36.7872);
        p.setHouseType("1BR");
        p.setImageUrls(List.of("a.jpg", "b.jpg", "c.jpg"));
        p.setLandlordPhone("+254700000000");
        p.setManagerRole("LANDLORD");
        return p;
    }

    private static List<String> codes(Property p) {
        return PublicationPolicy.validate(p).stream().map(PublicationViolation::code).toList();
    }

    @Test
    @DisplayName("a complete listing has no violations")
    void completeListingPasses() {
        assertTrue(PublicationPolicy.validate(validListing()).isEmpty());
    }

    @Test
    @DisplayName("a missing or blank title is rejected")
    void titleRequired() {
        for (String title : Arrays.asList(null, "", "   ")) {
            Property p = validListing();
            p.setTitle(title);
            assertEquals(List.of("TITLE_REQUIRED"), codes(p), "title=" + title);
        }
    }

    @Test
    @DisplayName("a zero or negative price is rejected")
    void priceMustBePositive() {
        for (double price : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            Property p = validListing();
            p.setPrice(price);
            assertEquals(List.of("PRICE_INVALID"), codes(p), "price=" + price);
        }
    }

    @Test
    @DisplayName("a missing location text is rejected")
    void locationRequired() {
        Property p = validListing();
        p.setLocation(" ");
        assertEquals(List.of("LOCATION_REQUIRED"), codes(p));
    }

    @Test
    @DisplayName("missing coordinates are rejected, as are out-of-range ones")
    void coordinatesRequiredAndValid() {
        Property noLat = validListing();
        noLat.setLatitude(null);
        assertEquals(List.of("COORDINATES_REQUIRED"), codes(noLat));

        Property noLng = validListing();
        noLng.setLongitude(null);
        assertEquals(List.of("COORDINATES_REQUIRED"), codes(noLng));

        Property outOfRange = validListing();
        outOfRange.setLatitude(91.0);
        assertEquals(List.of("COORDINATES_INVALID"), codes(outOfRange));
    }

    @Test
    @DisplayName("an unknown or missing property type is rejected")
    void houseTypeMustBeKnown() {
        for (String type : Arrays.asList(null, "", "PENTHOUSE")) {
            Property p = validListing();
            p.setHouseType(type);
            assertEquals(List.of("HOUSE_TYPE_INVALID"), codes(p), "houseType=" + type);
        }
    }

    @Test
    @DisplayName("fewer than the minimum photos is rejected; blank entries don't count")
    void minimumPhotos() {
        Property twoPhotos = validListing();
        twoPhotos.setImageUrls(List.of("a.jpg", "b.jpg"));
        assertEquals(List.of("PHOTOS_MIN"), codes(twoPhotos));

        Property blankEntries = validListing();
        blankEntries.setImageUrls(Arrays.asList("a.jpg", "b.jpg", " ", null));
        assertEquals(List.of("PHOTOS_MIN"), codes(blankEntries));

        Property noList = validListing();
        noList.setImageUrls(null);
        assertEquals(List.of("PHOTOS_MIN"), codes(noList));
    }

    @Test
    @DisplayName("a missing public contact phone is rejected")
    void contactPhoneRequired() {
        Property p = validListing();
        p.setLandlordPhone(" ");
        assertEquals(List.of("CONTACT_PHONE_REQUIRED"), codes(p));
    }

    @Test
    @DisplayName("a caretaker or agent listing needs the on-site contact's details")
    void caretakerDetailsRequiredForNonLandlordRoles() {
        for (String role : List.of("CARETAKER", "AGENT")) {
            Property p = validListing();
            p.setManagerRole(role);
            assertEquals(List.of("CARETAKER_NAME_REQUIRED", "CARETAKER_PHONE_REQUIRED"), codes(p), role);

            p.setCaretakerName("Wanjiru");
            p.setCaretakerPhone("+254711111111");
            assertTrue(codes(p).isEmpty(), role + " with contact details");
        }
    }

    @Test
    @DisplayName("a landlord listing needs no caretaker details")
    void landlordNeedsNoCaretaker() {
        Property landlord = validListing();
        landlord.setManagerRole("LANDLORD");
        assertTrue(codes(landlord).isEmpty());

        Property noRole = validListing();
        noRole.setManagerRole(null);
        assertTrue(codes(noRole).isEmpty());
    }

    @Test
    @DisplayName("an owner without a verified phone is rejected")
    void ownerPhoneMustBeVerified() {
        Property unverified = validListing();
        unverified.getOwner().setPhoneVerified(false);
        assertEquals(List.of("OWNER_PHONE_UNVERIFIED"), codes(unverified));

        Property noOwner = validListing();
        noOwner.setOwner(null);
        assertEquals(List.of("OWNER_PHONE_UNVERIFIED"), codes(noOwner));
    }

    @Test
    @DisplayName("every failure is reported at once, in a stable order")
    void reportsAllViolationsTogether() {
        assertEquals(List.of(
                "TITLE_REQUIRED",
                "PRICE_INVALID",
                "LOCATION_REQUIRED",
                "COORDINATES_REQUIRED",
                "HOUSE_TYPE_INVALID",
                "PHOTOS_MIN",
                "CONTACT_PHONE_REQUIRED",
                "OWNER_PHONE_UNVERIFIED"), codes(new Property()));
    }

    @Test
    @DisplayName("each violation names the offending field and carries a message")
    void violationsCarryFieldAndMessage() {
        Property p = validListing();
        p.setTitle(null);

        PublicationViolation v = PublicationPolicy.validate(p).get(0);
        assertEquals("title", v.field());
        assertEquals("TITLE_REQUIRED", v.code());
        assertFalse(v.message().isBlank());
    }
}
