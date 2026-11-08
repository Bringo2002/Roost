package com.roost.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link AddressAreaPolicy}; no network or Spring needed. */
class AddressAreaPolicyTest {

    // A neighbourhood-sized box (roughly Kilimani, Nairobi).
    private static final GeocodedPlace KILIMANI =
            new GeocodedPlace("Kilimani, Nairobi", -1.2922, 36.7914, -1.2994, -1.2851, 36.7771, 36.8057);
    private static final GeocodedPlace WESTLANDS =
            new GeocodedPlace("Westlands, Nairobi", -1.2676, 36.8108, -1.2750, -1.2600, 36.7990, 36.8230);

    @Test
    @DisplayName("a pin inside the area's box is within it")
    void pinInsideTheBox() {
        assertTrue(AddressAreaPolicy.isWithin(-1.2920, 36.7900, KILIMANI, 0));
    }

    @Test
    @DisplayName("a pin in another part of town is not within it")
    void pinElsewhere() {
        assertFalse(AddressAreaPolicy.isWithin(-1.2676, 36.8108, KILIMANI, 1000));
    }

    @Test
    @DisplayName("a pin just outside the box is accepted within the margin and rejected beyond it")
    void marginWidensTheBox() {
        double justNorth = -1.2851 + 0.0045; // roughly 500 m north of the box
        assertTrue(AddressAreaPolicy.isWithin(justNorth, 36.7900, KILIMANI, 1000));
        assertFalse(AddressAreaPolicy.isWithin(justNorth, 36.7900, KILIMANI, 100));
    }

    @Test
    @DisplayName("the margin applies east and west as well as north and south")
    void marginAppliesToLongitude() {
        double justEast = 36.8057 + 0.0045; // roughly 500 m east of the box
        assertTrue(AddressAreaPolicy.isWithin(-1.2920, justEast, KILIMANI, 1000));
        assertFalse(AddressAreaPolicy.isWithin(-1.2920, justEast, KILIMANI, 100));
    }

    @Test
    @DisplayName("the box edges are inclusive")
    void edgesAreInclusive() {
        assertTrue(AddressAreaPolicy.isWithin(-1.2851, 36.8057, KILIMANI, 0));
        assertTrue(AddressAreaPolicy.isWithin(-1.2994, 36.7771, KILIMANI, 0));
    }

    @Test
    @DisplayName("within any of several candidate places is enough")
    void anyCandidateCounts() {
        List<GeocodedPlace> candidates = List.of(WESTLANDS, KILIMANI);

        assertTrue(AddressAreaPolicy.isWithinAny(-1.2920, 36.7900, candidates, 0));
        assertFalse(AddressAreaPolicy.isWithinAny(-1.2100, 36.9500, candidates, 1000));
    }

    @Test
    @DisplayName("no candidate places means not within")
    void noCandidates() {
        assertFalse(AddressAreaPolicy.isWithinAny(-1.2920, 36.7900, List.of(), 1000));
    }
}
