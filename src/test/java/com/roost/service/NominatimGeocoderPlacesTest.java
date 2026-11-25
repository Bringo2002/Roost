package com.roost.service;

import com.roost.service.NominatimGeocoder.Transport;
import com.roost.service.PlaceFinder.FoundPlace;
import com.roost.service.PlaceFinder.PlaceKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline tests for the place search in {@link NominatimGeocoder}. The
 * responses use the names and coordinates Nominatim returned for the Muthaiga
 * area (Nairobi) on 24 Nov 2026, in its {@code jsonv2} shape.
 */
class NominatimGeocoderPlacesTest {

    private static final double LAT = -1.2540;
    private static final double LNG = 36.8350;

    private static final String HOSPITALS = """
            [{"place_id":1,"lat":"-1.2617938","lon":"36.8238411","category":"amenity","type":"hospital",
              "name":"Aga Khan University Hospital","display_name":"Aga Khan University Hospital, Nairobi, Kenya"},
             {"place_id":2,"lat":"-1.2556285","lon":"36.8316329","category":"amenity","type":"hospital",
              "name":"Gertrude's Children Hospital","display_name":"Gertrude's Children Hospital, Nairobi, Kenya"},
             {"place_id":3,"lat":"-1.2554706","lon":"36.8317204","category":"amenity","type":"hospital",
              "name":"Muthaiga Pediatrics","display_name":"Muthaiga Pediatrics, Nairobi, Kenya"}]""";

    private static final String MALLS = """
            [{"place_id":10,"lat":"-1.2758276","lon":"36.8488946","category":"shop","type":"mall",
              "name":"Iftin Shopping Mall","display_name":"Iftin Shopping Mall, Nairobi, Kenya"},
             {"place_id":11,"lat":"-1.2755994","lon":"36.8488469","category":"shop","type":"mall",
              "name":"Darul Salam Shopping Centre","display_name":"Darul Salam Shopping Centre, Nairobi, Kenya"}]""";

    private static final String CENTRES = """
            [{"place_id":11,"lat":"-1.2755994","lon":"36.8488469","category":"shop","type":"mall",
              "name":"Darul Salam Shopping Centre","display_name":"Darul Salam Shopping Centre, Nairobi, Kenya"},
             {"place_id":12,"lat":"-1.2540000","lon":"36.8400000","category":"amenity","type":"marketplace",
              "name":"Muthaiga Shopping Centre","display_name":"Muthaiga Shopping Centre, Nairobi, Kenya"}]""";

    private final Deque<Transport.Response> responses = new ArrayDeque<>();
    private final List<URI> requested = new ArrayList<>();
    private final List<Long> sleeps = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private NominatimGeocoder geocoder;

    @BeforeEach
    void setUp() {
        Transport transport = uri -> {
            requested.add(uri);
            Transport.Response next = responses.poll();
            if (next == null) {
                throw new IOException("no response queued");
            }
            return next;
        };
        geocoder = new NominatimGeocoder(transport, sleeps::add, now::get);
    }

    private void respond(int status, String body) {
        responses.add(new Transport.Response(status, body));
    }

    private static double[] viewbox(URI uri) {
        String query = uri.getRawQuery();
        String value = null;
        for (String part : query.split("&")) {
            if (part.startsWith("viewbox=")) {
                value = part.substring("viewbox=".length());
            }
        }
        String[] numbers = value.split(",");
        double[] box = new double[4];
        for (int i = 0; i < 4; i++) {
            box[i] = Double.parseDouble(numbers[i]);
        }
        return box;
    }

    @Test
    @DisplayName("hospitals are searched inside a box around the point")
    void searchesHospitalsInsideABoxAroundThePoint() throws Exception {
        respond(200, HOSPITALS);

        List<FoundPlace> places = geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000);

        assertEquals(3, places.size());
        assertEquals("Aga Khan University Hospital", places.get(0).name());
        assertEquals(-1.2617938, places.get(0).latitude(), 1e-9);
        assertEquals(36.8238411, places.get(0).longitude(), 1e-9);

        assertEquals(1, requested.size());
        String query = requested.get(0).getRawQuery();
        assertTrue(query.contains("amenity=hospital"));
        assertTrue(query.contains("bounded=1"));
        assertTrue(query.contains("limit=40"));
        assertTrue(query.contains("format=jsonv2"));

        // west, north, east, south: about 3 km each way (0.02695 degrees)
        double[] box = viewbox(requested.get(0));
        assertEquals(LNG - 0.02695, box[0], 2e-4);
        assertEquals(LAT + 0.02695, box[1], 2e-4);
        assertEquals(LNG + 0.02695, box[2], 2e-4);
        assertEquals(LAT - 0.02695, box[3], 2e-4);
    }

    @Test
    @DisplayName("malls are searched by two terms and a place found twice is listed once")
    void searchesMallsByTwoTermsAndMergesDuplicates() throws Exception {
        respond(200, MALLS);
        respond(200, CENTRES);

        List<FoundPlace> places = geocoder.findNear(PlaceKind.MALL, LAT, LNG, 3_000);

        assertEquals(2, requested.size());
        assertTrue(requested.get(0).getRawQuery().contains("q=mall"));
        assertTrue(requested.get(1).getRawQuery().contains("q=shopping+centre"));
        assertEquals(List.of("Iftin Shopping Mall", "Darul Salam Shopping Centre", "Muthaiga Shopping Centre"),
                places.stream().map(FoundPlace::name).toList());
    }

    @Test
    @DisplayName("a genuine empty answer is an empty list, not an error")
    void emptyAnswerIsAnEmptyList() throws Exception {
        respond(200, "[]");

        assertEquals(List.of(), geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000));
    }

    @Test
    @DisplayName("a result with no name takes the first part of its display name")
    void unnamedResultUsesTheDisplayName() throws Exception {
        respond(200, """
                [{"lat":"-1.25","lon":"36.83","display_name":"Ola Plaza, Thika Road, Nairobi, Kenya"}]""");
        respond(200, "[]"); // malls are searched by two terms, so there is a second request

        List<FoundPlace> places = geocoder.findNear(PlaceKind.MALL, LAT, LNG, 3_000);

        assertEquals("Ola Plaza", places.get(0).name());
    }

    @Test
    @DisplayName("a result with no name at all is skipped")
    void nameslessResultIsSkipped() throws Exception {
        respond(200, """
                [{"lat":"-1.25","lon":"36.83","name":"","display_name":""},
                 {"lat":"-1.26","lon":"36.84","name":"Real Hospital"}]""");

        List<FoundPlace> places = geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000);

        assertEquals(List.of("Real Hospital"), places.stream().map(FoundPlace::name).toList());
    }

    @Test
    @DisplayName("an HTTP error means the lookup is unavailable")
    void httpErrorIsUnavailable() {
        respond(502, "bad gateway");

        assertThrows(GeocodingUnavailableException.class,
                () -> geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000));
    }

    @Test
    @DisplayName("unreadable JSON means the lookup is unavailable")
    void unreadableJsonIsUnavailable() {
        respond(200, "<html>busy</html>");

        assertThrows(GeocodingUnavailableException.class,
                () -> geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000));
    }

    @Test
    @DisplayName("entries that can't be read at all mean the lookup is unavailable, not 'none found'")
    void unreadableEntriesAreUnavailable() {
        respond(200, """
                [{"lat":"north","lon":"east","name":"Somewhere"}]""");

        assertThrows(GeocodingUnavailableException.class,
                () -> geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000));
    }

    @Test
    @DisplayName("a repeat search for the same spot is answered from the cache")
    void repeatSearchIsCached() throws Exception {
        respond(200, HOSPITALS);

        geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000);
        List<FoundPlace> again = geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000);

        assertEquals(3, again.size());
        assertEquals(1, requested.size());
    }

    @Test
    @DisplayName("a different spot is searched separately")
    void differentSpotIsNotServedFromTheCache() throws Exception {
        respond(200, HOSPITALS);
        respond(200, "[]");

        geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000);
        List<FoundPlace> elsewhere = geocoder.findNear(PlaceKind.HOSPITAL, -1.30, 36.70, 3_000);

        assertEquals(List.of(), elsewhere);
        assertEquals(2, requested.size());
    }

    @Test
    @DisplayName("a failure is never cached")
    void failureIsNotCached() throws Exception {
        respond(502, "bad gateway");
        respond(200, HOSPITALS);

        assertThrows(GeocodingUnavailableException.class,
                () -> geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000));
        List<FoundPlace> retry = geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 3_000);

        assertEquals(3, retry.size());
        assertEquals(2, requested.size());
    }

    @Test
    @DisplayName("requests are kept the minimum interval apart")
    void requestsAreThrottled() throws Exception {
        respond(200, MALLS);
        respond(200, CENTRES);

        geocoder.findNear(PlaceKind.MALL, LAT, LNG, 3_000);

        assertEquals(List.of(NominatimGeocoder.MIN_REQUEST_INTERVAL_MILLIS), sleeps);
    }

    @Test
    @DisplayName("a missing position or radius is rejected")
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> geocoder.findNear(PlaceKind.HOSPITAL, Double.NaN, LNG, 3_000));
        assertThrows(IllegalArgumentException.class,
                () -> geocoder.findNear(PlaceKind.HOSPITAL, LAT, LNG, 0));
        assertEquals(0, requested.size());
    }
}
