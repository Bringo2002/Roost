package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyMyListingsPageTest {

    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private PropertyService propertyService;

    private final User owner = new User();

    private SliceImpl<Property> emptySlice() {
        return new SliceImpl<>(List.of(), PageRequest.of(0, 20), false);
    }

    @Test
    @DisplayName("passes the owner, filter and page request through to the repository")
    void delegatesToRepositoryWithPageRequest() {
        var slice = new SliceImpl<>(List.of(new Property()), PageRequest.of(2, 10), true);
        var counts = mock(PropertyRepository.OwnerListingCounts.class);
        when(propertyRepository.findOwnerListingsPage(eq(owner), eq("DRAFT"), any(Pageable.class)))
                .thenReturn(slice);
        when(propertyRepository.countOwnerListings(owner)).thenReturn(counts);

        PropertyService.MyListingsPage result = propertyService.getMyListingsPage(owner, "DRAFT", 2, 10);

        assertSame(slice, result.listings());
        assertSame(counts, result.counts());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(propertyRepository).findOwnerListingsPage(eq(owner), eq("DRAFT"), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        // The ORDER BY lives in the query; a Sort here would be appended to it.
        assertEquals(false, pageable.getValue().getSort().isSorted());
    }

    @Test
    @DisplayName("filter is case-insensitive and trimmed")
    void normalisesFilter() {
        when(propertyRepository.findOwnerListingsPage(eq(owner), eq("PUBLISHED"), any(Pageable.class)))
                .thenReturn(emptySlice());

        propertyService.getMyListingsPage(owner, "  published ", 0, 20);

        verify(propertyRepository).findOwnerListingsPage(eq(owner), eq("PUBLISHED"), any(Pageable.class));
    }

    @Test
    @DisplayName("a null or blank filter means ALL")
    void nullOrBlankFilterDefaultsToAll() {
        when(propertyRepository.findOwnerListingsPage(eq(owner), eq("ALL"), any(Pageable.class)))
                .thenReturn(emptySlice());

        propertyService.getMyListingsPage(owner, null, 0, 20);
        propertyService.getMyListingsPage(owner, "   ", 0, 20);

        verify(propertyRepository, org.mockito.Mockito.times(2))
                .findOwnerListingsPage(eq(owner), eq("ALL"), any(Pageable.class));
    }

    @Test
    @DisplayName("an unknown filter is a 400 and never reaches the database")
    void unknownFilterIsBadRequest() {
        ApiException ex = assertThrows(ApiException.class,
                () -> propertyService.getMyListingsPage(owner, "EVERYTHING", 0, 20));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(propertyRepository, never()).findOwnerListingsPage(any(), any(), any());
        verify(propertyRepository, never()).countOwnerListings(any());
    }
}
