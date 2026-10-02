package com.roost.service;

import com.roost.dto.ListingEventBatchRequest;
import com.roost.dto.ListingEventBatchResponse;
import com.roost.model.ListingEvent;
import com.roost.model.ListingEventType;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.ListingEventRepository;
import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingEventServiceTest {

    @Mock
    private ListingEventRepository listingEventRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Captor
    private ArgumentCaptor<List<ListingEvent>> rowsCaptor;

    @Captor
    private ArgumentCaptor<Collection<Long>> idsCaptor;

    private ListingEventService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ListingEventService(listingEventRepository, propertyRepository);
        user = new User();
        user.setId(7L);
    }

    private static ListingEventBatchRequest.Item item(long propertyId, ListingEventType type) {
        return new ListingEventBatchRequest.Item(propertyId, type);
    }

    private static Property property(long id) {
        Property p = new Property();
        p.setId(id);
        return p;
    }

    @Test
    @DisplayName("stores events for existing listings, drops unknown ones, and says how many of each")
    void storesExistingAndDropsUnknown() {
        Property p1 = property(1L);
        when(propertyRepository.findExistingIds(any())).thenReturn(List.of(1L));
        when(propertyRepository.getReferenceById(1L)).thenReturn(p1);

        ListingEventBatchResponse response = service.record(user, List.of(
                item(1L, ListingEventType.IMPRESSION),
                item(2L, ListingEventType.CLICK),
                item(1L, ListingEventType.CLICK)));

        assertEquals(2, response.accepted());
        assertEquals(1, response.dropped());
        verify(listingEventRepository).saveAll(rowsCaptor.capture());
        List<ListingEvent> rows = rowsCaptor.getValue();
        assertEquals(2, rows.size());
        assertEquals("IMPRESSION", rows.get(0).getEventType());
        assertEquals("CLICK", rows.get(1).getEventType());
        for (ListingEvent row : rows) {
            assertSame(p1, row.getProperty());
            assertEquals(7L, row.getUserId());
            assertNotNull(row.getCreatedAt());
        }
        verify(propertyRepository, times(1)).getReferenceById(1L);
    }

    @Test
    @DisplayName("checks existence once for the whole batch, with distinct ids")
    void checksExistenceOnceWithDistinctIds() {
        when(propertyRepository.findExistingIds(any())).thenReturn(List.of(1L, 2L));
        when(propertyRepository.getReferenceById(1L)).thenReturn(property(1L));
        when(propertyRepository.getReferenceById(2L)).thenReturn(property(2L));

        service.record(user, List.of(
                item(1L, ListingEventType.IMPRESSION),
                item(1L, ListingEventType.CLICK),
                item(2L, ListingEventType.IMPRESSION)));

        verify(propertyRepository, times(1)).findExistingIds(idsCaptor.capture());
        assertEquals(Set.of(1L, 2L), new HashSet<>(idsCaptor.getValue()));
        assertEquals(2, idsCaptor.getValue().size());
    }

    @Test
    @DisplayName("a batch where every listing is unknown stores nothing")
    void allUnknownStoresNothing() {
        when(propertyRepository.findExistingIds(any())).thenReturn(List.of());

        ListingEventBatchResponse response = service.record(user, List.of(
                item(8L, ListingEventType.CLICK),
                item(9L, ListingEventType.CLICK)));

        assertEquals(0, response.accepted());
        assertEquals(2, response.dropped());
        verify(listingEventRepository).saveAll(rowsCaptor.capture());
        assertEquals(0, rowsCaptor.getValue().size());
    }

    @Test
    @DisplayName("an empty batch touches no repository (an empty IN () would be invalid SQL)")
    void emptyBatchTouchesNothing() {
        ListingEventBatchResponse response = service.record(user, List.of());

        assertEquals(0, response.accepted());
        assertEquals(0, response.dropped());
        verifyNoInteractions(listingEventRepository, propertyRepository);
    }
}
