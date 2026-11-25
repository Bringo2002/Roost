package com.roost.service;

import com.roost.service.NearbyFacilitiesService.Facility;
import com.roost.service.PlaceFinder.FoundPlace;
import com.roost.service.PlaceFinder.PlaceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link NominatimFacilitySource} with a fake {@link PlaceFinder}.
 * The places and coordinates are real ones Nominatim returned around Muthaiga
 * (Nairobi); the distances were worked out from them with the haversine
 * formula.
 */
class NominatimFacilitySourceTest {

    private static final double LAT = -1.2540;
    private static final double LNG = 36.8350;

    private static final FoundPlace AGA_KHAN = new FoundPlace("Aga Khan University Hospital", -1.2617938, 36.8238411); // 1513 m
    private static final FoundPlace GERTRUDES = new FoundPlace("Gertrude's Children Hospital", -1.2556285, 36.8316329); // 416 m
    private static final FoundPlace PEDIATRICS = new FoundPlace("Muthaiga Pediatrics", -1.2554706, 36.8317204); // 400 m
    private static final FoundPlace ANKA = new FoundPlace("Anka Hospital", -1.2775578, 36.8481309); // 2999 m
    private static final FoundPlace IFTIN = new FoundPlace("Iftin Shopping Mall", -1.2758276, 36.8488946); // 2877 m
    private static final FoundPlace DARUL_SALAM = new FoundPlace("Darul Salam Shopping Centre", -1.2755994, 36.8488469); // 2853 m
    private static final FoundPlace KEEKOROK = new FoundPlace("Keekorok Shopping Mall", -1.2808735, 36.8240287); // 3228 m

    private static NominatimFacilitySource source(List<FoundPlace> hospitals, List<FoundPlace> malls) {
        return new NominatimFacilitySource((kind, lat, lng, radius) ->
                kind == PlaceKind.HOSPITAL ? hospitals : malls);
    }

    @Test
    @DisplayName("returns the nearest hospital and the nearest mall, nearest first")
    void returnsTheNearestOfEachKind() {
        var result = source(List.of(AGA_KHAN, GERTRUDES, PEDIATRICS), List.of(IFTIN, DARUL_SALAM))
                .lookup(LAT, LNG);

        List<Facility> facilities = result.orElseThrow();
        assertEquals(2, facilities.size());

        assertEquals("Muthaiga Pediatrics", facilities.get(0).name());
        assertEquals("hospital", facilities.get(0).category());
        assertEquals(399.6, facilities.get(0).distanceMeters(), 1.0);

        assertEquals("Darul Salam Shopping Centre", facilities.get(1).name());
        assertEquals("mall", facilities.get(1).category());
        assertEquals(2852.7, facilities.get(1).distanceMeters(), 1.0);
    }

    @Test
    @DisplayName("a place beyond the search radius is left out")
    void dropsPlacesBeyondTheRadius() {
        var result = source(List.of(GERTRUDES), List.of(KEEKOROK)).lookup(LAT, LNG);

        List<Facility> facilities = result.orElseThrow();
        assertEquals(1, facilities.size());
        assertEquals("hospital", facilities.get(0).category());
    }

    @Test
    @DisplayName("a place just inside the radius is kept")
    void keepsAPlaceJustInsideTheRadius() {
        var result = source(List.of(ANKA), List.of()).lookup(LAT, LNG);

        List<Facility> facilities = result.orElseThrow();
        assertEquals(1, facilities.size());
        assertEquals("Anka Hospital", facilities.get(0).name());
    }

    @Test
    @DisplayName("nothing nearby is an empty list, which is a real answer")
    void nothingNearbyIsAnEmptyList() {
        Optional<List<Facility>> result = source(List.of(), List.of()).lookup(LAT, LNG);

        assertEquals(Optional.of(List.of()), result);
    }

    @Test
    @DisplayName("a failed hospital search gives no answer at all")
    void failedHospitalSearchGivesNoAnswer() {
        var source = new NominatimFacilitySource((kind, lat, lng, radius) -> {
            if (kind == PlaceKind.HOSPITAL) {
                throw new GeocodingUnavailableException("Nominatim answered HTTP 502");
            }
            return List.of(DARUL_SALAM);
        });

        assertEquals(Optional.empty(), source.lookup(LAT, LNG));
    }

    @Test
    @DisplayName("a failed mall search gives no answer at all, so a half-answer is never stored")
    void failedMallSearchGivesNoAnswer() {
        var source = new NominatimFacilitySource((kind, lat, lng, radius) -> {
            if (kind == PlaceKind.MALL) {
                throw new GeocodingUnavailableException("Nominatim answered HTTP 502");
            }
            return List.of(GERTRUDES);
        });

        assertEquals(Optional.empty(), source.lookup(LAT, LNG));
    }

    @Test
    @DisplayName("searches both kinds around the listing, within 3 km")
    void searchesBothKindsAroundTheListing() {
        List<String> calls = new ArrayList<>();
        var source = new NominatimFacilitySource((kind, lat, lng, radius) -> {
            calls.add(kind + "@" + lat + "," + lng + "/" + radius);
            return List.of();
        });

        source.lookup(LAT, LNG);

        assertEquals(List.of("MALL@" + LAT + "," + LNG + "/3000", "HOSPITAL@" + LAT + "," + LNG + "/3000"), calls);
        assertTrue(NominatimFacilitySource.SEARCH_RADIUS_METERS == 3_000);
    }
}
