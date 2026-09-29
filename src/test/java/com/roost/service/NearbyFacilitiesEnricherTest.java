package com.roost.service;

import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NearbyFacilitiesEnricherTest {

    @Mock
    private NearbyFacilitiesService nearbyFacilitiesService;
    @Mock
    private PropertyRepository propertyRepository;

    private NearbyFacilitiesEnricher enricher;

    private NearbyFacilitiesEnricher newEnricher() {
        return new NearbyFacilitiesEnricher(nearbyFacilitiesService, propertyRepository);
    }

    @Test
    void storesFacilitiesAsJsonAndReturnsTrueOnSuccess() {
        var facility = new NearbyFacilitiesService.Facility("TRM Mall", "mall", 600.0, -1.29, 36.82);
        when(nearbyFacilitiesService.lookup(-1.28, 36.81)).thenReturn(Optional.of(List.of(facility)));
        when(propertyRepository.updateNearbyFacilities(eq(42L), anyString())).thenReturn(1);
        enricher = newEnricher();

        boolean result = enricher.enrich(42L, -1.28, 36.81);

        assertTrue(result);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(propertyRepository).updateNearbyFacilities(eq(42L), jsonCaptor.capture());
        assertTrue(jsonCaptor.getValue().contains("TRM Mall"));
        assertTrue(jsonCaptor.getValue().contains("\"category\":\"mall\""));
    }

    @Test
    void storesAnEmptyJsonArrayWhenGenuinelyNothingNearby() {
        when(nearbyFacilitiesService.lookup(-1.28, 36.81)).thenReturn(Optional.of(List.of()));
        when(propertyRepository.updateNearbyFacilities(eq(42L), anyString())).thenReturn(1);
        enricher = newEnricher();

        assertTrue(enricher.enrich(42L, -1.28, 36.81));
        verify(propertyRepository).updateNearbyFacilities(42L, "[]");
    }

    @Test
    void doesNotWriteAnythingWhenLookupFails() {
        when(nearbyFacilitiesService.lookup(-1.28, 36.81)).thenReturn(Optional.empty());
        enricher = newEnricher();

        boolean result = enricher.enrich(42L, -1.28, 36.81);

        assertFalse(result);
        verify(propertyRepository, never()).updateNearbyFacilities(anyLong(), anyString());
    }

    @Test
    void returnsFalseIfTheRowWasGoneByTheTimeItWroteBack() {
        when(nearbyFacilitiesService.lookup(-1.28, 36.81)).thenReturn(Optional.of(List.of()));
        when(propertyRepository.updateNearbyFacilities(eq(42L), anyString())).thenReturn(0); // no row matched
        enricher = newEnricher();

        assertFalse(enricher.enrich(42L, -1.28, 36.81));
    }
}
