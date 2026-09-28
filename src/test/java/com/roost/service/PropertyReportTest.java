package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.model.PropertyReport;
import com.roost.model.User;
import com.roost.repository.PropertyRepository;
import com.roost.repository.PropertyReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyReportTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PropertyReportRepository propertyReportRepository;

    @Mock
    private FirebasePushService firebasePushService;

    @Mock
    private PropertyRiskService propertyRiskService;

    @InjectMocks
    private PropertyService propertyService;

    private User owner;
    private User reporter1;
    private Property property;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");

        reporter1 = new User();
        reporter1.setId(2L);
        reporter1.setEmail("reporter1@example.com");

        property = new Property();
        property.setId(100L);
        property.setTitle("Luxury Apartment");
        property.setOwner(owner);
        property.setStatus("PUBLISHED");
    }

    @Test
    @DisplayName("Should successfully create a report when parameters are valid")
    void reportProperty_success() {
        when(propertyRepository.findById(100L)).thenReturn(Optional.of(property));
        when(propertyReportRepository.existsByPropertyAndReportedBy(property, reporter1)).thenReturn(false);
        when(propertyReportRepository.countByProperty(property)).thenReturn(1L);

        PropertyReport result = propertyService.reportProperty(100L, reporter1, "Inaccurate pricing", "Price is higher than listed");

        assertNotNull(result);
        assertEquals(property, result.getProperty());
        assertEquals(reporter1, result.getReportedBy());
        assertEquals("Inaccurate pricing", result.getReason());
        assertEquals("Price is higher than listed", result.getDetails());

        verify(propertyReportRepository).saveAndFlush(any(PropertyReport.class));
        verify(propertyRiskService).recompute(property);
        verify(propertyRepository).save(property);
        assertEquals("PUBLISHED", property.getStatus());
    }

    @Test
    @DisplayName("Should throw ApiException when owner tries to report their own property")
    void reportProperty_selfReport_throwsException() {
        when(propertyRepository.findById(100L)).thenReturn(Optional.of(property));

        ApiException exception = assertThrows(ApiException.class, () ->
                propertyService.reportProperty(100L, owner, "Spam", "Self reporting test")
        );

        assertEquals("You cannot report your own listing.", exception.getMessage());
        verify(propertyReportRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should throw ApiException when user has already reported the property")
    void reportProperty_duplicateReport_throwsException() {
        when(propertyRepository.findById(100L)).thenReturn(Optional.of(property));
        when(propertyReportRepository.existsByPropertyAndReportedBy(property, reporter1)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () ->
                propertyService.reportProperty(100L, reporter1, "Spam", "Duplicate report test")
        );

        assertEquals("You've already reported this listing.", exception.getMessage());
        verify(propertyReportRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should throw ApiException when database unique constraint violation occurs during race condition")
    void reportProperty_dbConstraintViolation_throwsException() {
        when(propertyRepository.findById(100L)).thenReturn(Optional.of(property));
        when(propertyReportRepository.existsByPropertyAndReportedBy(property, reporter1)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("Unique index violation"))
                .when(propertyReportRepository).saveAndFlush(any(PropertyReport.class));

        ApiException exception = assertThrows(ApiException.class, () ->
                propertyService.reportProperty(100L, reporter1, "Spam", "Race condition test")
        );

        assertEquals("You've already reported this listing.", exception.getMessage());
    }

    @Test
    @DisplayName("Should update property status to UNDER_REVIEW and send notification when threshold is reached")
    void reportProperty_thresholdCrossed_hidesProperty() {
        when(propertyRepository.findById(100L)).thenReturn(Optional.of(property));
        when(propertyReportRepository.existsByPropertyAndReportedBy(property, reporter1)).thenReturn(false);
        when(propertyReportRepository.countByProperty(property)).thenReturn(3L);

        PropertyReport result = propertyService.reportProperty(100L, reporter1, "Fraudulent listing", "Scam details");

        assertNotNull(result);
        assertEquals("UNDER_REVIEW", property.getStatus());
        verify(propertyRepository).save(property);
        verify(firebasePushService).sendToUser(eq(owner), eq("Listing under review"), anyString(), anyMap());
    }

    @Test
    @DisplayName("getFlaggedForReview counts reports in one batched query, not one per property, and sorts by count")
    void getFlaggedForReview_batchesCountsAndSortsDescending() {
        Property second = new Property();
        second.setId(101L);
        Property third = new Property();
        third.setId(102L);
        when(propertyReportRepository.findPropertiesWithUnreviewedReports())
                .thenReturn(new ArrayList<>(List.of(property, second, third)));

        PropertyReportRepository.PropertyReportCount forFirst = mock(PropertyReportRepository.PropertyReportCount.class);
        when(forFirst.getPropertyId()).thenReturn(100L);
        when(forFirst.getReportCount()).thenReturn(1L);
        PropertyReportRepository.PropertyReportCount forSecond = mock(PropertyReportRepository.PropertyReportCount.class);
        when(forSecond.getPropertyId()).thenReturn(101L);
        when(forSecond.getReportCount()).thenReturn(5L);
        // 102 has no row in the grouped result -> must default to 0.
        when(propertyReportRepository.countReportsByPropertyIds(any()))
                .thenReturn(List.of(forFirst, forSecond));

        List<Property> result = propertyService.getFlaggedForReview();

        assertEquals(List.of(101L, 100L, 102L), result.stream().map(Property::getId).toList());
        assertEquals(5L, result.get(0).getReportCount());
        assertEquals(1L, result.get(1).getReportCount());
        assertEquals(0L, result.get(2).getReportCount());
        verify(propertyReportRepository, times(1)).countReportsByPropertyIds(any());
        verify(propertyReportRepository, never()).countByProperty(any());
    }

    @Test
    @DisplayName("getFlaggedForReview with nothing flagged skips the count query entirely")
    void getFlaggedForReview_empty_skipsCountQuery() {
        when(propertyReportRepository.findPropertiesWithUnreviewedReports()).thenReturn(new ArrayList<>());

        assertTrue(propertyService.getFlaggedForReview().isEmpty());

        verify(propertyReportRepository, never()).countReportsByPropertyIds(any());
    }
}
