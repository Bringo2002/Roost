package com.roost.service;

import com.roost.model.ListingRankScore;
import com.roost.model.Property;
import com.roost.repository.ListingEventRepository;
import com.roost.repository.ListingRankScoreRepository;
import com.roost.repository.PropertyRepository;
import com.roost.service.ListingRankingFormula.Result;
import com.roost.service.ListingRankingFormula.Signals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingRankingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.of(2026, 10, 5, 12, 0);

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private ListingEventRepository listingEventRepository;

    @Mock
    private ListingRankScoreRepository listingRankScoreRepository;

    @Captor
    private ArgumentCaptor<List<ListingRankScore>> savedCaptor;

    @Captor
    private ArgumentCaptor<Instant> sinceCaptor;

    private ListingRankingService service;

    @BeforeEach
    void setUp() {
        service = new ListingRankingService(
                propertyRepository, listingEventRepository, listingRankScoreRepository, 7);
    }

    // ---- small stand-ins for the repositories' projection interfaces ----

    private static final class Input implements PropertyRepository.RankingInput {
        private final Long id;
        private final LocalDateTime listedAt;
        private final Boolean photoApproved;
        private final Boolean verified;
        private final Boolean gpsVerified;
        private final Boolean communityVerified;

        Input(Long id, LocalDateTime listedAt, Boolean photoApproved, Boolean verified,
              Boolean gpsVerified, Boolean communityVerified) {
            this.id = id;
            this.listedAt = listedAt;
            this.photoApproved = photoApproved;
            this.verified = verified;
            this.gpsVerified = gpsVerified;
            this.communityVerified = communityVerified;
        }

        @Override public Long getId() { return id; }
        @Override public LocalDateTime getListedAt() { return listedAt; }
        @Override public Boolean getPhotoApproved() { return photoApproved; }
        @Override public Boolean getVerified() { return verified; }
        @Override public Boolean getGpsVerified() { return gpsVerified; }
        @Override public Boolean getCommunityVerified() { return communityVerified; }
    }

    private static final class Count implements ListingEventRepository.EventCount {
        private final Long propertyId;
        private final String eventType;
        private final long eventCount;

        Count(Long propertyId, String eventType, long eventCount) {
            this.propertyId = propertyId;
            this.eventType = eventType;
            this.eventCount = eventCount;
        }

        @Override public Long getPropertyId() { return propertyId; }
        @Override public String getEventType() { return eventType; }
        @Override public long getEventCount() { return eventCount; }
    }

    private static final class Flags implements PropertyRepository.RiskFlagCount {
        private final Long propertyId;
        private final long flagCount;

        Flags(Long propertyId, long flagCount) {
            this.propertyId = propertyId;
            this.flagCount = flagCount;
        }

        @Override public Long getPropertyId() { return propertyId; }
        @Override public long getFlagCount() { return flagCount; }
    }

    private static Property propertyRef(long id) {
        Property p = new Property();
        p.setId(id);
        return p;
    }

    private void stubNoHistory() {
        when(listingEventRepository.countByPropertyAndTypeSince(any())).thenReturn(List.of());
        when(propertyRepository.countRiskFlagsOfPublished()).thenReturn(List.of());
        when(listingRankScoreRepository.findAll()).thenReturn(List.of());
    }

    @Test
    @DisplayName("scores a new listing from its attributes, flags and event counts, using the trailing window")
    void scoresNewListingFromAllSignals() {
        Property ref = propertyRef(1L);
        when(propertyRepository.findRankingInputs()).thenReturn(List.of(
                new Input(1L, NOW_LOCAL.minusDays(3), true, true, false, null)));
        when(listingEventRepository.countByPropertyAndTypeSince(any())).thenReturn(List.of(
                new Count(1L, "IMPRESSION", 40), new Count(1L, "CLICK", 4), new Count(1L, "SAVE", 1)));
        when(propertyRepository.countRiskFlagsOfPublished()).thenReturn(List.of(new Flags(1L, 1)));
        when(listingRankScoreRepository.findAll()).thenReturn(List.of());
        when(propertyRepository.getReferenceById(1L)).thenReturn(ref);

        int scored = service.recompute(CLOCK);

        Result expected = ListingRankingFormula.score(
                new Signals(3.0, true, true, false, false, 1, 40, 4, 1, 0, 0));
        assertEquals(1, scored);
        verify(listingRankScoreRepository).saveAll(savedCaptor.capture());
        ListingRankScore saved = savedCaptor.getValue().get(0);
        assertSame(ref, saved.getProperty());
        assertEquals(expected.score(), saved.getScore(), 1e-12);
        assertEquals(expected.exposureBoost(), saved.getExposureBoost(), 1e-12);
        assertEquals(NOW, saved.getComputedAt());

        verify(listingEventRepository).countByPropertyAndTypeSince(sinceCaptor.capture());
        assertEquals(NOW.minus(Duration.ofDays(7)), sinceCaptor.getValue());
    }

    @Test
    @DisplayName("updates an existing score row in place instead of creating a second one")
    void updatesExistingRow() {
        ListingRankScore existing = mock(ListingRankScore.class);
        when(existing.getPropertyId()).thenReturn(1L);
        when(propertyRepository.findRankingInputs()).thenReturn(List.of(
                new Input(1L, NOW_LOCAL.minusDays(1), true, true, true, true)));
        when(listingEventRepository.countByPropertyAndTypeSince(any())).thenReturn(List.of());
        when(propertyRepository.countRiskFlagsOfPublished()).thenReturn(List.of());
        when(listingRankScoreRepository.findAll()).thenReturn(List.of(existing));

        service.recompute(CLOCK);

        Result expected = ListingRankingFormula.score(
                new Signals(1.0, true, true, true, true, 0, 0, 0, 0, 0, 0));
        verify(existing).update(eq(expected.score()), eq(expected.exposureBoost()), eq(NOW));
        verify(propertyRepository, never()).getReferenceById(anyLong());
        verify(listingRankScoreRepository).saveAll(savedCaptor.capture());
        assertSame(existing, savedCaptor.getValue().get(0));
    }

    @Test
    @DisplayName("a missing listing date counts as old, and a future date as brand new")
    void handlesMissingAndFutureListedAt() {
        when(propertyRepository.findRankingInputs()).thenReturn(List.of(
                new Input(1L, null, true, true, true, true),
                new Input(2L, NOW_LOCAL.plusDays(2), true, true, true, true)));
        stubNoHistory();
        when(propertyRepository.getReferenceById(anyLong())).thenReturn(propertyRef(9L));

        service.recompute(CLOCK);

        verify(listingRankScoreRepository).saveAll(savedCaptor.capture());
        List<ListingRankScore> saved = savedCaptor.getValue();
        double unknownAge = ListingRankingFormula.score(
                new Signals(ListingRankingService.UNKNOWN_AGE_DAYS, true, true, true, true, 0, 0, 0, 0, 0, 0)).score();
        double brandNew = ListingRankingFormula.score(
                new Signals(0.0, true, true, true, true, 0, 0, 0, 0, 0, 0)).score();
        assertEquals(unknownAge, saved.get(0).getScore(), 1e-12);
        assertEquals(brandNew, saved.get(1).getScore(), 1e-12);
    }

    @Test
    @DisplayName("ignores unknown event types and events for listings that are not being scored")
    void ignoresUnknownTypesAndOtherListings() {
        when(propertyRepository.findRankingInputs()).thenReturn(List.of(
                new Input(1L, NOW_LOCAL.minusDays(2), false, false, false, false)));
        when(listingEventRepository.countByPropertyAndTypeSince(any())).thenReturn(List.of(
                new Count(1L, "SWIPE", 99), new Count(1L, "IMPRESSION", 10), new Count(2L, "CLICK", 500)));
        when(propertyRepository.countRiskFlagsOfPublished()).thenReturn(List.of(new Flags(2L, 3)));
        when(listingRankScoreRepository.findAll()).thenReturn(List.of());
        when(propertyRepository.getReferenceById(1L)).thenReturn(propertyRef(1L));

        service.recompute(CLOCK);

        Result expected = ListingRankingFormula.score(
                new Signals(2.0, false, false, false, false, 0, 10, 0, 0, 0, 0));
        verify(listingRankScoreRepository).saveAll(savedCaptor.capture());
        assertEquals(expected.score(), savedCaptor.getValue().get(0).getScore(), 1e-12);
    }

    @Test
    @DisplayName("with no visible listings it does nothing else and reports zero")
    void noListingsDoesNothing() {
        when(propertyRepository.findRankingInputs()).thenReturn(List.of());

        int scored = service.recompute(CLOCK);

        assertEquals(0, scored);
        verifyNoInteractions(listingEventRepository, listingRankScoreRepository);
        verify(propertyRepository, never()).countRiskFlagsOfPublished();
    }

    @Test
    @DisplayName("writes scores in chunks so one transaction never accumulates the whole catalogue")
    void writesInChunks() {
        List<PropertyRepository.RankingInput> inputs = new ArrayList<>();
        for (long id = 1; id <= 1200; id++) {
            inputs.add(new Input(id, NOW_LOCAL.minusDays(1), true, true, true, true));
        }
        when(propertyRepository.findRankingInputs()).thenReturn(inputs);
        stubNoHistory();
        when(propertyRepository.getReferenceById(anyLong())).thenReturn(propertyRef(1L));

        int scored = service.recompute(CLOCK);

        assertEquals(1200, scored);
        verify(listingRankScoreRepository, times(3)).saveAll(savedCaptor.capture());
        List<List<ListingRankScore>> chunks = savedCaptor.getAllValues();
        assertEquals(500, chunks.get(0).size());
        assertEquals(500, chunks.get(1).size());
        assertEquals(200, chunks.get(2).size());
    }

    @Test
    @DisplayName("rejects a window shorter than one day")
    void rejectsInvalidWindow() {
        assertThrows(IllegalArgumentException.class, () -> new ListingRankingService(
                propertyRepository, listingEventRepository, listingRankScoreRepository, 0));
    }
}
