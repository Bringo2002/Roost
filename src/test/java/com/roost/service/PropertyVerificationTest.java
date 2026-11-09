package com.roost.service;

import com.roost.model.Property;
import com.roost.model.User;
import com.roost.service.PropertyVerification.AuthorityState;
import com.roost.service.PropertyVerification.DocumentsState;
import com.roost.service.PropertyVerification.LocationState;
import com.roost.service.PropertyVerification.OnSiteState;
import com.roost.service.PropertyVerification.PhoneState;
import com.roost.service.PropertyVerification.PhotosState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rule-by-rule tests for {@link PropertyVerification}; no Spring context needed. */
class PropertyVerificationTest {

    private static Property listing() {
        User owner = new User();
        owner.setId(1L);
        Property p = new Property();
        p.setOwner(owner);
        return p;
    }

    private static PropertyVerification of(Property p) {
        return PropertyVerification.from(p);
    }

    @Test
    @DisplayName("a new listing has nothing checked yet")
    void aNewListingHasNothingChecked() {
        PropertyVerification v = of(listing());

        assertEquals(PhoneState.UNVERIFIED, v.phone());
        assertEquals(LocationState.UNCONFIRMED, v.location());
        assertEquals(OnSiteState.NOT_ATTEMPTED, v.onSite());
        assertEquals(PhotosState.NOT_SUBMITTED, v.photos());
        assertEquals(DocumentsState.NOT_SUBMITTED, v.documents());
        assertEquals(AuthorityState.NOT_ESTABLISHED, v.authority());
        assertFalse(v.badgeEarned());
    }

    @Test
    @DisplayName("the phone is verified only once the owner's number is confirmed")
    void phoneState() {
        Property p = listing();
        assertEquals(PhoneState.UNVERIFIED, of(p).phone());

        p.getOwner().setPhoneVerified(true);
        assertEquals(PhoneState.VERIFIED, of(p).phone());

        p.setOwner(null);
        assertEquals(PhoneState.UNVERIFIED, of(p).phone());
    }

    @Test
    @DisplayName("the location is confirmed only when both coordinates are pinned")
    void locationState() {
        Property p = listing();
        p.setLatitude(-1.29);
        assertEquals(LocationState.UNCONFIRMED, of(p).location());

        p.setLongitude(36.78);
        assertEquals(LocationState.CONFIRMED, of(p).location());
    }

    @Test
    @DisplayName("on-site presence passes only after the server verified it")
    void onSiteState() {
        Property p = listing();
        p.setLatitude(-1.29);
        p.setLongitude(36.78);
        assertEquals(OnSiteState.NOT_ATTEMPTED, of(p).onSite());

        p.setGpsVerified(true);
        assertEquals(OnSiteState.PASSED, of(p).onSite());
    }

    @Test
    @DisplayName("photos go from not submitted, to submitted, to approved or rejected")
    void photosState() {
        Property p = listing();
        assertEquals(PhotosState.NOT_SUBMITTED, of(p).photos());

        p.setImageUrls(List.of("a.jpg"));
        assertEquals(PhotosState.SUBMITTED, of(p).photos());

        p.setPhotoRejected(true);
        assertEquals(PhotosState.REJECTED, of(p).photos());

        p.setPhotoApproved(true);
        assertEquals(PhotosState.APPROVED, of(p).photos(), "an approval wins over an earlier rejection");
    }

    @Test
    @DisplayName("blank or missing photo entries don't count as submitted")
    void blankPhotosAreNotSubmitted() {
        Property p = listing();
        p.setImageUrls(Arrays.asList(" ", null, ""));
        assertEquals(PhotosState.NOT_SUBMITTED, of(p).photos());

        p.setImageUrls(null);
        assertEquals(PhotosState.NOT_SUBMITTED, of(p).photos());
    }

    @Test
    @DisplayName("uploading a document never implies it was accepted")
    void uploadedIsNotAccepted() {
        Property p = listing();
        assertEquals(DocumentsState.NOT_SUBMITTED, of(p).documents());

        p.setDocumentUrls(List.of("title-deed.pdf"));
        assertEquals(DocumentsState.UPLOADED, of(p).documents());

        p.setDocumentVerified(true);
        assertEquals(DocumentsState.ACCEPTED, of(p).documents());
    }

    @Test
    @DisplayName("the badge needs a verified phone, a passed on-site check and approved photos")
    void badgeNeedsAllThree() {
        Property p = listing();
        p.getOwner().setPhoneVerified(true);
        p.setGpsVerified(true);
        p.setPhotoApproved(true);
        assertTrue(of(p).badgeEarned());

        p.getOwner().setPhoneVerified(false);
        assertFalse(of(p).badgeEarned(), "without a verified phone");
        p.getOwner().setPhoneVerified(true);

        p.setGpsVerified(false);
        assertFalse(of(p).badgeEarned(), "without the on-site check");
        p.setGpsVerified(true);

        p.setPhotoApproved(false);
        p.setImageUrls(List.of("a.jpg"));
        assertFalse(of(p).badgeEarned(), "with photos submitted but not approved");
    }

    @Test
    @DisplayName("documents don't affect the badge, whatever their state")
    void documentsDontAffectTheBadge() {
        Property p = listing();
        p.getOwner().setPhoneVerified(true);
        p.setGpsVerified(true);
        p.setPhotoApproved(true);
        p.setDocumentUrls(List.of("title-deed.pdf"));

        assertEquals(DocumentsState.UPLOADED, of(p).documents());
        assertTrue(of(p).badgeEarned());
    }
}
