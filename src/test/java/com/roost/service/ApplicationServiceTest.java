package com.roost.service;

import com.roost.dto.ApplicationRequestDto;
import com.roost.exception.ApiException;
import com.roost.model.Application;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ApplicationService#submitApplication}. */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private PropertyService propertyService;
    @Mock private ListingEventService listingEventService;

    @InjectMocks
    private ApplicationService applicationService;

    private User applicant;
    private Property property;

    @BeforeEach
    void setUp() {
        applicant = new User();
        applicant.setId(1L);

        property = new Property();
        property.setId(10L);
    }

    private static ApplicationRequestDto request(Long propertyId, String fullName) {
        ApplicationRequestDto request = new ApplicationRequestDto();
        request.setPropertyId(propertyId);
        request.setFullName(fullName);
        return request;
    }

    @Test
    @DisplayName("submitApplication saves a PENDING application with every field from the request")
    void savesApplicationFromRequest() {
        ApplicationRequestDto request = request(10L, "Jane Doe");
        request.setNationalId("12345678");
        request.setEmploymentStatus("EMPLOYED");
        request.setMonthlyIncome(85000.0);
        when(propertyService.getPropertyById(10L)).thenReturn(property);
        when(applicationRepository.existsByPropertyAndApplicant(property, applicant)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        Application saved = applicationService.submitApplication(applicant, request);

        assertEquals(property, saved.getProperty());
        assertEquals(applicant, saved.getApplicant());
        assertEquals("Jane Doe", saved.getFullName());
        assertEquals("12345678", saved.getNationalId());
        assertEquals("EMPLOYED", saved.getEmploymentStatus());
        assertEquals(85000.0, saved.getMonthlyIncome());
        assertEquals("PENDING", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("optional fields left out default to empty text and zero income, as before")
    void optionalFieldsDefault() {
        when(propertyService.getPropertyById(10L)).thenReturn(property);
        when(applicationRepository.existsByPropertyAndApplicant(property, applicant)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        Application saved = applicationService.submitApplication(applicant, request(10L, "Jane Doe"));

        assertEquals("", saved.getNationalId());
        assertEquals("", saved.getEmploymentStatus());
        assertEquals(0.0, saved.getMonthlyIncome());
    }

    @Test
    @DisplayName("a second application by the same applicant for the same property is rejected")
    void duplicateApplicationIsRejected() {
        when(propertyService.getPropertyById(10L)).thenReturn(property);
        when(applicationRepository.existsByPropertyAndApplicant(property, applicant)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> applicationService.submitApplication(applicant, request(10L, "Jane Doe")));

        assertEquals("You have already applied for this property", ex.getMessage());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown property surfaces the property lookup's not-found error and saves nothing")
    void unknownPropertyIsNotFound() {
        when(propertyService.getPropertyById(99L)).thenThrow(ApiException.notFound("Property not found with id: 99"));

        assertThrows(ApiException.class,
                () -> applicationService.submitApplication(applicant, request(99L, "Jane Doe")));

        verify(applicationRepository, never()).save(any());
    }
}
