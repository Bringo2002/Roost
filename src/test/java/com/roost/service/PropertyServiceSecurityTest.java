package com.roost.service;

import com.roost.exception.ApiException;
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

        Property updated = new Property();
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

        Property updated = new Property();
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

        Property updated = new Property();
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

        Property updated = new Property();
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
}
