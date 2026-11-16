package com.roost.service;

import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The apartment/building name is entered in the listing wizard, which creates
 * a draft early and then PUTs every later save. {@code updateProperty} is a
 * full overwrite, so it has to carry the name across -- otherwise the name
 * never reaches the feed or the detail page.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceBuildingNameTest {

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

    private Property existing;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(1L);
        owner.setName("Test Owner");

        existing = new Property();
        existing.setId(10L);
        existing.setOwner(owner);
        existing.setTitle("Test Listing");
        existing.setStatus("DRAFT");

        when(propertyRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Property draftUpdate() {
        Property p = new Property();
        // Property defaults to PUBLISHED, which would run the publication
        // policy; these tests are about field copying, so ask for a draft.
        p.setStatus("DRAFT");
        p.setTitle("Sunny 1BR in Kilimani");
        p.setPrice(25000);
        p.setLocation("Kilimani");
        p.setHouseType("1BR");
        p.setImageUrls(List.of("a.jpg", "b.jpg", "c.jpg"));
        p.setLandlordPhone("+254700000000");
        return p;
    }

    @Test
    @DisplayName("updateProperty stores the building name sent by the client")
    void updateProperty_savesBuildingName() {
        Property updated = draftUpdate();
        updated.setBuildingName("Sunrise Apartments");

        Property result = propertyService.updateProperty(10L, updated);

        assertEquals("Sunrise Apartments", result.getBuildingName());
    }

    @Test
    @DisplayName("updateProperty clears the building name when the client sends none")
    void updateProperty_clearsBuildingNameWhenOmitted() {
        existing.setBuildingName("Old Name");

        Property result = propertyService.updateProperty(10L, draftUpdate());

        assertNull(result.getBuildingName());
    }
}
