package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Application;
import com.roost.model.ListingEvent;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.ApplicationRepository;
import com.roost.repository.ListingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private PropertyService propertyService;

    @Mock
    private ListingEventRepository listingEventRepository;

    private ApplicationService service;
    private User applicant;
    private Property property;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, propertyService, listingEventRepository);
        applicant = new User();
        applicant.setId(7L);
        property = new Property();
        property.setId(42L);
    }

    @Test
    @DisplayName("a submitted application records one APPLICATION event for the listing and applicant")
    void recordsApplicationEvent() {
        when(propertyService.getPropertyById(42L)).thenReturn(property);
        when(applicationRepository.existsByPropertyAndApplicant(property, applicant)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        Application result = service.submitApplication(applicant, Map.of("propertyId", 42, "fullName", "Jane Doe"));

        assertEquals("PENDING", result.getStatus());
        ArgumentCaptor<ListingEvent> captor = ArgumentCaptor.forClass(ListingEvent.class);
        verify(listingEventRepository).save(captor.capture());
        ListingEvent event = captor.getValue();
        assertSame(property, event.getProperty());
        assertEquals(7L, event.getUserId());
        assertEquals("APPLICATION", event.getEventType());
    }

    @Test
    @DisplayName("a duplicate application is rejected and records no event")
    void duplicateRecordsNothing() {
        when(propertyService.getPropertyById(42L)).thenReturn(property);
        when(applicationRepository.existsByPropertyAndApplicant(property, applicant)).thenReturn(true);

        assertThrows(ApiException.class,
                () -> service.submitApplication(applicant, Map.of("propertyId", 42, "fullName", "Jane Doe")));

        verifyNoInteractions(listingEventRepository);
    }
}
