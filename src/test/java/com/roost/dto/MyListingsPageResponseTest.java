package com.roost.dto;

import com.roost.model.Property;
import com.roost.repository.PropertyRepository.OwnerListingCounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyListingsPageResponseTest {

    @Test
    @DisplayName("maps page metadata, items and counts from a Slice")
    void mapsSliceAndCounts() {
        Property p = new Property();
        p.setTitle("Cozy Bedsitter");
        p.setLocation("Kilimani");
        p.setPrice(25000);
        p.setStatus("DRAFT");
        var slice = new SliceImpl<>(List.of(p), PageRequest.of(1, 20), true);

        OwnerListingCounts c = mock(OwnerListingCounts.class);
        when(c.getTotal()).thenReturn(42L);
        when(c.getDrafts()).thenReturn(3L);
        when(c.getAvailable()).thenReturn(30L);
        when(c.getRented()).thenReturn(9L);
        when(c.getVerified()).thenReturn(25L);

        MyListingsPageResponse r = MyListingsPageResponse.from(slice, c);

        assertEquals(1, r.page());
        assertEquals(20, r.size());
        assertTrue(r.hasNext());
        assertEquals(1, r.items().size());
        assertEquals("Cozy Bedsitter", r.items().get(0).title());
        assertEquals("DRAFT", r.items().get(0).status());
        assertEquals(new MyListingsPageResponse.Counts(42, 3, 30, 9, 25), r.counts());
    }

    @Test
    @DisplayName("null count buckets (SUM over zero rows) become 0, and a missing counts row is all zeros")
    void nullCountsBecomeZero() {
        var slice = new SliceImpl<Property>(List.of(), PageRequest.of(0, 20), false);

        OwnerListingCounts sparse = mock(OwnerListingCounts.class);
        when(sparse.getTotal()).thenReturn(0L); // COUNT is 0; every SUM is null

        assertEquals(new MyListingsPageResponse.Counts(0, 0, 0, 0, 0),
                MyListingsPageResponse.from(slice, sparse).counts());
        assertEquals(new MyListingsPageResponse.Counts(0, 0, 0, 0, 0),
                MyListingsPageResponse.from(slice, null).counts());
        assertFalse(MyListingsPageResponse.from(slice, null).hasNext());
    }

    @Test
    @DisplayName("the list item omits landlordPhone and defaults a null managerRole to LANDLORD")
    void listItemShapeAndDefaults() {
        Property p = new Property();
        p.setLandlordPhone("+254700000000");
        p.setManagerRole(null);

        PropertyListItemDto dto = PropertyListItemDto.from(p);

        assertEquals("LANDLORD", dto.managerRole());
        assertTrue(java.util.Arrays.stream(PropertyListItemDto.class.getRecordComponents())
                .noneMatch(rc -> rc.getName().equals("landlordPhone")));
    }
}
