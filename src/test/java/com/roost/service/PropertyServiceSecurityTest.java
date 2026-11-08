package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.exception.PublicationPolicyException;
import com.roost.exception.PublicationViolation;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Security-focused tests for {@link PropertyService}: status override
 * protection, mass-assignment sanitization, and endorsement token generation.
 * Written Red-first — these fail against the pre-fix codebase.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceSecurityTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private com.roost.repository.UserRepository userRepository;
    @Mock private com.roost.repository.ReviewRepository reviewRepository;
    @Mock private com.roost.repository.PropertyReportRepository propertyReportRepository;
    @Mock private com.roost.repository.CommunityCheckRepository communityCheckRepository;
    @Mock private com.roost.repository.ApplicationRepository applicationRepository;
    @Mock private FirebasePushService firebasePushService;
    @Mock private NearbyFacilitiesService nearbyFacilitiesService;
    @Mock private PropertyRiskService propertyRiskService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private GpsReadingPolicy gpsReadingPolicy;
    @Mock private AddressAreaCheck addressAreaCheck;

    @InjectMocks
    private PropertyService propertyService;

    private User verifiedOwner;
    private Property existingProperty;

    @BeforeEach
    void setUp() {
        verifiedOwner = new User();
        verifiedOwner.setId(1L);
        verifiedOwner.setName("Test Owner");
        verifiedOwner.setPhoneVerified(true);

        existingProperty = new Property();
        existingProperty.setId(10L);
        existingProperty.setTitle("Test Listing");
        existingProperty.setOwner(verifiedOwner);
        existingProperty.setStatus("PUBLISHED");
    }

    /** A listing that satisfies the publication policy, owned by {@link #verifiedOwner}. */
    private Property validListing() {
        Property p = new Property();
        p.setOwner(verifiedOwner);
        p.setTitle("Sunny 1BR in Kilimani");
        p.setPrice(25000);
        p.setLocation("Kilimani");
        p.setLatitude(-1.2921);
        p.setLongitude(36.7872);
        p.setHouseType("1BR");
        p.setImageUrls(List.of("a.jpg", "b.jpg", "c.jpg"));
        p.setLandlordPhone("+254700000000");
        return p;
    }

    private static List<String> codes(PublicationPolicyException ex) {
        return ex.getViolations().stream().map(PublicationViolation::code).toList();
    }

    // ── H1: Status override from UNDER_REVIEW ──────────────────────────────

    @Test
    @DisplayName("updateProperty rejects status change when listing is UNDER_REVIEW")
    void updateProperty_rejectsStatusChangeFromUnderReview() {
        existingProperty.setStatus("UNDER_REVIEW");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        Property updated = new Property();
        updated.setStatus("PUBLISHED");

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.updateProperty(10L, updated));
        assertTrue(ex.getMessage().toLowerCase().contains("review"),
                "Error message should mention review: " + ex.getMessage());
    }

    @Test
    @DisplayName("publishDraft rejects listing that is UNDER_REVIEW")
    void publishDraft_rejectsUnderReviewListing() {
        existingProperty.setStatus("UNDER_REVIEW");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.publishDraft(10L));
        assertTrue(ex.getMessage().toLowerCase().contains("review"),
                "Error message should mention review: " + ex.getMessage());
    }

    @Test
    @DisplayName("updateProperty allows legitimate DRAFT → PUBLISHED transition")
    void updateProperty_allowsDraftToPublished() {
        existingProperty.setStatus("DRAFT");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Property updated = validListing();
        updated.setStatus("PUBLISHED");

        Property result = propertyService.updateProperty(10L, updated);
        assertEquals("PUBLISHED", result.getStatus());
    }

    // ── H2: Mass-assignment of trust fields in updateProperty ───────────────

    @Test
    @DisplayName("updateProperty does not copy documentVerified from request body")
    void updateProperty_doesNotCopyDocumentVerified() {
        existingProperty.setDocumentVerified(false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Property updated = validListing();
        updated.setDocumentVerified(true);

        Property result = propertyService.updateProperty(10L, updated);
        assertFalse(result.getDocumentVerified(),
                "documentVerified should not be overwritten by client input");
    }

    @Test
    @DisplayName("updateProperty does not copy landlordEndorsed from request body")
    void updateProperty_doesNotCopyLandlordEndorsed() {
        existingProperty.setLandlordEndorsed(false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Property updated = validListing();
        updated.setLandlordEndorsed(true);

        Property result = propertyService.updateProperty(10L, updated);
        assertFalse(result.isLandlordEndorsed(),
                "landlordEndorsed should not be overwritten by client input");
    }

    @Test
    @DisplayName("updateProperty does not copy endorsementToken from request body")
    void updateProperty_doesNotCopyEndorsementToken() {
        existingProperty.setEndorsementToken("server-generated-token");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Property updated = validListing();
        updated.setEndorsementToken("attacker-chosen-token");

        Property result = propertyService.updateProperty(10L, updated);
        assertEquals("server-generated-token", result.getEndorsementToken(),
                "endorsementToken should not be overwritten by client input");
    }

    // ── M5: Endorsement token must be server-generated ──────────────────────

    @Test
    @DisplayName("addProperty generates a server-side endorsement token")
    void addProperty_serverGeneratesEndorsementToken() {
        Property input = new Property();
        input.setTitle("New Listing");
        input.setOwner(verifiedOwner);
        input.setStatus("DRAFT");
        input.setEndorsementToken("client-chosen"); // client tries to set it

        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> {
                    Property saved = inv.getArgument(0);
                    saved.setId(99L);
                    return saved;
                });

        Property result = propertyService.addProperty(input);
        assertNotNull(result.getEndorsementToken(),
                "endorsementToken should be generated server-side");
        assertNotEquals("client-chosen", result.getEndorsementToken(),
                "endorsementToken must not be the client-supplied value");
        // Should be a valid UUID format
        assertDoesNotThrow(() -> java.util.UUID.fromString(result.getEndorsementToken()),
                "endorsementToken should be a valid UUID");
    }

    // ── H1 supplement: Status whitelist ───────────────────────────────────

    @Test
    @DisplayName("updateProperty rejects an arbitrary/invalid status value")
    void updateProperty_rejectsArbitraryStatus() {
        existingProperty.setStatus("DRAFT");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        Property updated = new Property();
        updated.setStatus("HACKED");

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.updateProperty(10L, updated));
        assertTrue(ex.getMessage().toLowerCase().contains("status"),
                "Error message should mention status: " + ex.getMessage());
    }

    @Test
    @DisplayName("updateProperty rejects landlord setting status to UNDER_REVIEW")
    void updateProperty_rejectsTransitionToUnderReview() {
        existingProperty.setStatus("PUBLISHED");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        Property updated = new Property();
        updated.setStatus("UNDER_REVIEW");

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.updateProperty(10L, updated));
        assertTrue(ex.getMessage().toLowerCase().contains("status"),
                "Error message should mention status: " + ex.getMessage());
    }

    // ── Published listings are never silently unpublished by an edit ──────

    @Test
    @DisplayName("updateProperty rejects PUBLISHED → DRAFT and leaves the listing live")
    void updateProperty_rejectsPublishedToDraft() {
        existingProperty.setStatus("PUBLISHED");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        Property updated = new Property();
        updated.setStatus("DRAFT");

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.updateProperty(10L, updated));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("updateProperty keeps a published listing PUBLISHED when status is unchanged")
    void updateProperty_keepsPublishedWhenStatusUnchanged() {
        existingProperty.setStatus("PUBLISHED");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Property updated = validListing();
        updated.setStatus("PUBLISHED");

        Property result = propertyService.updateProperty(10L, updated);
        assertEquals("PUBLISHED", result.getStatus());
    }

    @Test
    @DisplayName("updateProperty keeps a draft a DRAFT when saved as DRAFT")
    void updateProperty_keepsDraftWhenSavedAsDraft() {
        existingProperty.setStatus("DRAFT");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Property updated = new Property();
        updated.setStatus("DRAFT");

        Property result = propertyService.updateProperty(10L, updated);
        assertEquals("DRAFT", result.getStatus());
    }

    // ── Publication policy is enforced on every publish route ──────────────

    @Test
    @DisplayName("addProperty rejects an incomplete listing created as PUBLISHED")
    void addProperty_rejectsIncompletePublishedListing() {
        Property input = new Property();
        input.setOwner(verifiedOwner);
        input.setStatus("PUBLISHED");

        PublicationPolicyException ex = assertThrows(PublicationPolicyException.class,
                () -> propertyService.addProperty(input));
        assertTrue(codes(ex).contains("TITLE_REQUIRED"), "violations: " + codes(ex));
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("addProperty accepts a complete listing created as PUBLISHED")
    void addProperty_acceptsCompletePublishedListing() {
        Property input = validListing();
        input.setStatus("PUBLISHED");
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Property result = propertyService.addProperty(input);
        assertEquals("PUBLISHED", result.getStatus());
    }

    @Test
    @DisplayName("publishDraft rejects an incomplete draft")
    void publishDraft_rejectsIncompleteDraft() {
        existingProperty.setStatus("DRAFT");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        PublicationPolicyException ex = assertThrows(PublicationPolicyException.class,
                () -> propertyService.publishDraft(10L));
        assertTrue(codes(ex).contains("PRICE_INVALID"), "violations: " + codes(ex));
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("publishDraft still requires the owner's phone to be verified")
    void publishDraft_rejectsUnverifiedOwnerPhone() {
        Property draft = validListing();
        draft.setStatus("DRAFT");
        verifiedOwner.setPhoneVerified(false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(draft));

        PublicationPolicyException ex = assertThrows(PublicationPolicyException.class,
                () -> propertyService.publishDraft(10L));
        assertEquals(List.of("OWNER_PHONE_UNVERIFIED"), codes(ex));
    }

    @Test
    @DisplayName("publishDraft publishes a complete draft")
    void publishDraft_publishesCompleteDraft() {
        Property draft = validListing();
        draft.setStatus("DRAFT");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(draft));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Property result = propertyService.publishDraft(10L);
        assertEquals("PUBLISHED", result.getStatus());
    }

    @Test
    @DisplayName("updateProperty rejects publishing an incomplete draft")
    void updateProperty_rejectsPublishingIncompleteDraft() {
        existingProperty.setStatus("DRAFT");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        Property updated = validListing();
        updated.setTitle(" ");
        updated.setStatus("PUBLISHED");

        PublicationPolicyException ex = assertThrows(PublicationPolicyException.class,
                () -> propertyService.updateProperty(10L, updated));
        assertEquals(List.of("TITLE_REQUIRED"), codes(ex));
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("updateProperty rejects an edit that leaves a live listing incomplete")
    void updateProperty_rejectsEditThatBlanksLiveListing() {
        existingProperty.setStatus("PUBLISHED");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        Property updated = validListing();
        updated.setPrice(0);
        updated.setStatus("PUBLISHED");

        PublicationPolicyException ex = assertThrows(PublicationPolicyException.class,
                () -> propertyService.updateProperty(10L, updated));
        assertEquals(List.of("PRICE_INVALID"), codes(ex));
        verify(propertyRepository, never()).save(any(Property.class));
    }

    // ── GPS verification is decided by the server-side policy ──────────────

    private static GpsReading someReading() {
        return new GpsReading(-1.2921, 36.7872, 12.0, 1_800_000_000_000L);
    }

    @Test
    @DisplayName("verifyGpsLocation rejects every outcome except PASS and verifies nothing")
    void verifyGpsLocation_rejectsWhenPolicyRejects() {
        existingProperty.setLatitude(-1.2921);
        existingProperty.setLongitude(36.7872);
        existingProperty.setGpsVerified(false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        for (GpsReadingPolicy.Outcome outcome : List.of(
                GpsReadingPolicy.Outcome.INVALID,
                GpsReadingPolicy.Outcome.STALE,
                GpsReadingPolicy.Outcome.TOO_INACCURATE,
                GpsReadingPolicy.Outcome.TOO_FAR)) {
            when(gpsReadingPolicy.evaluate(anyDouble(), anyDouble(), any(GpsReading.class), anyLong()))
                    .thenReturn(new GpsReadingPolicy.Verdict(outcome, 350.0));

            ApiException ex = assertThrows(ApiException.class,
                    () -> propertyService.verifyGpsLocation(10L, someReading()), outcome.name());
            assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus(), outcome.name());
        }
        assertFalse(existingProperty.isGpsVerified());
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("verifyGpsLocation tells the lister how far away they are when too far")
    void verifyGpsLocation_tooFarReportsDistance() {
        existingProperty.setLatitude(-1.2921);
        existingProperty.setLongitude(36.7872);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(gpsReadingPolicy.evaluate(anyDouble(), anyDouble(), any(GpsReading.class), anyLong()))
                .thenReturn(new GpsReadingPolicy.Verdict(GpsReadingPolicy.Outcome.TOO_FAR, 350.0));

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.verifyGpsLocation(10L, someReading()));
        assertTrue(ex.getMessage().contains("350m"), ex.getMessage());
    }

    @Test
    @DisplayName("verifyGpsLocation marks the listing verified when the policy passes")
    void verifyGpsLocation_verifiesOnPass() {
        existingProperty.setLatitude(-1.2921);
        existingProperty.setLongitude(36.7872);
        existingProperty.setGpsVerified(false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
        GpsReading reading = someReading();
        when(gpsReadingPolicy.evaluate(eq(-1.2921), eq(36.7872), eq(reading), anyLong()))
                .thenReturn(new GpsReadingPolicy.Verdict(GpsReadingPolicy.Outcome.PASS, 4.0));
        existingProperty.setLocation("Kilimani, Nairobi");
        when(addressAreaCheck.check("Kilimani, Nairobi", "KE", -1.2921, 36.7872))
                .thenReturn(AddressAreaCheck.Outcome.MATCHES);

        Property result = propertyService.verifyGpsLocation(10L, reading);

        assertTrue(result.isGpsVerified());
        assertNotNull(result.getGpsVerifiedAt());
    }

    @Test
    @DisplayName("verifyGpsLocation needs a pinned location and doesn't consult the policy without one")
    void verifyGpsLocation_requiresPinnedLocation() {
        existingProperty.setLatitude(null);
        existingProperty.setLongitude(null);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));

        assertThrows(ApiException.class, () -> propertyService.verifyGpsLocation(10L, someReading()));
        verifyNoInteractions(gpsReadingPolicy);
    }

    // ── The pin must be in the area the address names ───────────────────────

    /** A listing at a pinned location whose fresh reading passes the GPS policy. */
    private void givenPassingReadingAt(String address) {
        existingProperty.setLatitude(-1.2921);
        existingProperty.setLongitude(36.7872);
        existingProperty.setLocation(address);
        existingProperty.setGpsVerified(false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(gpsReadingPolicy.evaluate(anyDouble(), anyDouble(), any(GpsReading.class), anyLong()))
                .thenReturn(new GpsReadingPolicy.Verdict(GpsReadingPolicy.Outcome.PASS, 4.0));
    }

    @Test
    @DisplayName("verifyGpsLocation refuses when the pin is outside the area the address names")
    void verifyGpsLocation_rejectsPinOutsideTheAddressArea() {
        givenPassingReadingAt("Westlands, Nairobi");
        when(addressAreaCheck.check(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(AddressAreaCheck.Outcome.OUTSIDE_AREA);

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.verifyGpsLocation(10L, someReading()));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Westlands, Nairobi"), ex.getMessage());
        assertFalse(existingProperty.isGpsVerified());
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("verifyGpsLocation asks for a better address when it matches nothing on the map")
    void verifyGpsLocation_rejectsAnUnrecognisedAddress() {
        givenPassingReadingAt("asdf qwerty");
        when(addressAreaCheck.check(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(AddressAreaCheck.Outcome.NOT_FOUND);

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.verifyGpsLocation(10L, someReading()));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Kilimani, Nairobi"), "should show an example: " + ex.getMessage());
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("verifyGpsLocation answers 503 and verifies nothing when the address can't be checked")
    void verifyGpsLocation_unavailableAddressCheckIsNotWavedThrough() {
        givenPassingReadingAt("Kilimani, Nairobi");
        when(addressAreaCheck.check(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(AddressAreaCheck.Outcome.UNAVAILABLE);

        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.verifyGpsLocation(10L, someReading()));

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertFalse(existingProperty.isGpsVerified());
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("verifyGpsLocation still verifies when the address check is switched off")
    void verifyGpsLocation_verifiesWhenAddressCheckIsSkipped() {
        givenPassingReadingAt("Kilimani, Nairobi");
        when(addressAreaCheck.check(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(AddressAreaCheck.Outcome.SKIPPED);
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(propertyService.verifyGpsLocation(10L, someReading()).isGpsVerified());
    }

    @Test
    @DisplayName("verifyGpsLocation needs an address, and doesn't geocode a blank one")
    void verifyGpsLocation_requiresAnAddress() {
        givenPassingReadingAt("   ");

        assertThrows(ApiException.class, () -> propertyService.verifyGpsLocation(10L, someReading()));
        verifyNoInteractions(addressAreaCheck);
    }

    @Test
    @DisplayName("verifyGpsLocation only checks the address once the reading itself has passed")
    void verifyGpsLocation_addressCheckComesAfterTheReadingCheck() {
        existingProperty.setLatitude(-1.2921);
        existingProperty.setLongitude(36.7872);
        existingProperty.setLocation("Kilimani, Nairobi");
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existingProperty));
        when(gpsReadingPolicy.evaluate(anyDouble(), anyDouble(), any(GpsReading.class), anyLong()))
                .thenReturn(new GpsReadingPolicy.Verdict(GpsReadingPolicy.Outcome.TOO_FAR, 900.0));

        assertThrows(ApiException.class, () -> propertyService.verifyGpsLocation(10L, someReading()));
        verifyNoInteractions(addressAreaCheck);
    }
}
