package com.roost.service;

import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NearbyFacilitiesBackfillTaskTest {

    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private NearbyFacilitiesEnricher enricher;

    private record Point(Long id, Double latitude, Double longitude) implements PropertyRepository.GeoPoint {
        @Override
        public Long getId() { return id; }
        @Override
        public Double getLatitude() { return latitude; }
        @Override
        public Double getLongitude() { return longitude; }
    }

    private NearbyFacilitiesBackfillTask task(boolean enabled, int batchSize, long pauseMillis) {
        return new NearbyFacilitiesBackfillTask(propertyRepository, enricher, enabled, batchSize, pauseMillis);
    }

    @Test
    void doesNothingWhenDisabled() {
        task(false, 20, 0).run();
        verifyNoInteractions(propertyRepository, enricher);
    }

    @Test
    void doesNothingWhenNoListingsAreMissingFacilities() {
        when(propertyRepository.findGpsVerifiedMissingNearbyFacilities(any(Pageable.class))).thenReturn(List.of());
        task(true, 20, 0).run();
        verifyNoInteractions(enricher);
    }

    @Test
    void enrichesEveryListingInTheBatchOnSuccess() {
        var batch = List.<PropertyRepository.GeoPoint>of(
                new Point(1L, -1.28, 36.81), new Point(2L, -1.29, 36.82), new Point(3L, -1.30, 36.83));
        when(propertyRepository.findGpsVerifiedMissingNearbyFacilities(any(Pageable.class))).thenReturn(batch);
        when(enricher.enrich(anyLong2(), anyDouble(), anyDouble())).thenReturn(true);

        task(true, 20, 0).run();

        verify(enricher).enrich(eq(1L), eq(-1.28), eq(36.81));
        verify(enricher).enrich(eq(2L), eq(-1.29), eq(36.82));
        verify(enricher).enrich(eq(3L), eq(-1.30), eq(36.83));
    }

    @Test
    void stopsEarlyAfterThreeConsecutiveFailures() {
        var batch = List.<PropertyRepository.GeoPoint>of(
                new Point(1L, -1.0, 36.0), new Point(2L, -1.0, 36.0), new Point(3L, -1.0, 36.0),
                new Point(4L, -1.0, 36.0), new Point(5L, -1.0, 36.0));
        when(propertyRepository.findGpsVerifiedMissingNearbyFacilities(any(Pageable.class))).thenReturn(batch);
        when(enricher.enrich(anyLong2(), anyDouble(), anyDouble())).thenReturn(false);

        task(true, 20, 0).run();

        // exactly MAX_CONSECUTIVE_FAILURES attempts, then the run bails
        verify(enricher, times(NearbyFacilitiesBackfillTask.MAX_CONSECUTIVE_FAILURES)).enrich(anyLong2(), anyDouble(), anyDouble());
    }

    @Test
    void aSuccessResetsTheConsecutiveFailureCount() {
        // fail, fail, succeed, fail, fail, succeed: never 3 in a row, so all 6 run.
        var batch = List.<PropertyRepository.GeoPoint>of(
                new Point(1L, -1.0, 36.0), new Point(2L, -1.0, 36.0), new Point(3L, -1.0, 36.0),
                new Point(4L, -1.0, 36.0), new Point(5L, -1.0, 36.0), new Point(6L, -1.0, 36.0));
        when(propertyRepository.findGpsVerifiedMissingNearbyFacilities(any(Pageable.class))).thenReturn(batch);
        when(enricher.enrich(anyLong2(), anyDouble(), anyDouble()))
                .thenReturn(false, false, true, false, false, true);

        task(true, 20, 0).run();

        verify(enricher, times(6)).enrich(anyLong2(), anyDouble(), anyDouble());
    }

    @Test
    void aFailureLoadingTheBatchIsSwallowed() {
        when(propertyRepository.findGpsVerifiedMissingNearbyFacilities(any(Pageable.class)))
                .thenThrow(new RuntimeException("db unavailable"));

        assertDoesNotThrow(() -> task(true, 20, 0).run());
        verifyNoInteractions(enricher);
    }

    private static long anyLong2() { return org.mockito.ArgumentMatchers.anyLong(); }
}
