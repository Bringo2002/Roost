package com.roost.service;

import com.roost.service.NominatimGeocoder.Transport;
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

/** Offline tests for {@link NominatimGeocoder}: transport, sleeper and clock are all faked. */
class NominatimGeocoderTest {

    private static final String KILIMANI = """
            [{"lat":"-1.2922","lon":"36.7914","display_name":"Kilimani, Nairobi, Kenya",
              "boundingbox":["-1.2994","-1.2851","36.7771","36.8057"]}]""";

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

    @Test
    @DisplayName("reads place name, point and bounding box from the response")
    void parsesPlaces() throws Exception {
        respond(200, KILIMANI);

        List<GeocodedPlace> places = geocoder.search("Kilimani, Nairobi", "KE");

        assertEquals(1, places.size());
        GeocodedPlace place = places.get(0);
        assertEquals("Kilimani, Nairobi, Kenya", place.displayName());
        assertEquals(-1.2922, place.latitude(), 1e-9);
        assertEquals(36.7914, place.longitude(), 1e-9);
        assertEquals(-1.2994, place.south(), 1e-9);
        assertEquals(-1.2851, place.north(), 1e-9);
        assertEquals(36.7771, place.west(), 1e-9);
        assertEquals(36.8057, place.east(), 1e-9);
    }

    @Test
    @DisplayName("asks Nominatim for JSON, limited to the country, with the text encoded")
    void buildsTheRequest() throws Exception {
        respond(200, KILIMANI);

        geocoder.search("Kilimani, Nairobi", "KE");

        String uri = requested.get(0).toString();
        assertTrue(uri.startsWith("https://nominatim.openstreetmap.org/search?"), uri);
        assertTrue(uri.contains("format=jsonv2"), uri);
        assertTrue(uri.contains("countrycodes=ke"), uri);
        assertTrue(uri.contains("q=kilimani%2C+nairobi"), uri);
    }

    @Test
    @DisplayName("an empty array means no match, not a failure")
    void emptyResultIsNoMatch() throws Exception {
        respond(200, "[]");

        assertTrue(geocoder.search("nowhere in particular", "KE").isEmpty());
    }

    @Test
    @DisplayName("a blank query returns nothing without calling Nominatim")
    void blankQueryMakesNoRequest() throws Exception {
        assertTrue(geocoder.search("   ", "KE").isEmpty());
        assertTrue(geocoder.search(null, "KE").isEmpty());
        assertTrue(requested.isEmpty());
    }

    @Test
    @DisplayName("the same text, however spaced or cased, is fetched once")
    void cachesRepeatedLookups() throws Exception {
        respond(200, KILIMANI);

        geocoder.search("Kilimani, Nairobi", "KE");
        geocoder.search("  kilimani,   NAIROBI ", "ke");

        assertEquals(1, requested.size());
    }

    @Test
    @DisplayName("a found place is cached for a week, a miss only for an hour")
    void cacheLifetimes() throws Exception {
        respond(200, KILIMANI);
        respond(200, "[]");

        geocoder.search("Kilimani", "KE");
        geocoder.search("Nowhere", "KE");
        assertEquals(2, requested.size());

        now.addAndGet(2 * 60 * 60 * 1000L); // two hours on
        geocoder.search("Kilimani", "KE");  // still cached
        assertEquals(2, requested.size());
        respond(200, "[]");
        geocoder.search("Nowhere", "KE");   // the miss has expired
        assertEquals(3, requested.size());

        now.addAndGet(8 * 24 * 60 * 60 * 1000L); // eight days on
        respond(200, KILIMANI);
        geocoder.search("Kilimani", "KE");
        assertEquals(4, requested.size());
    }

    @Test
    @DisplayName("a failed lookup is reported as unavailable and is never cached")
    void failuresAreNotCached() throws Exception {
        respond(503, "overloaded");
        assertThrows(GeocodingUnavailableException.class, () -> geocoder.search("Kilimani", "KE"));

        respond(200, KILIMANI);
        assertEquals(1, geocoder.search("Kilimani", "KE").size());
        assertEquals(2, requested.size());
    }

    @Test
    @DisplayName("a network error is reported as unavailable")
    void networkErrorIsUnavailable() {
        // nothing queued: the fake transport throws IOException
        assertThrows(GeocodingUnavailableException.class, () -> geocoder.search("Kilimani", "KE"));
    }

    @Test
    @DisplayName("an unreadable or unexpected body is unavailable, not 'not found'")
    void unreadableBodyIsUnavailable() {
        for (String body : List.of("<html>oops</html>", "{\"error\":\"rate limited\"}",
                "[{\"lat\":\"x\",\"lon\":\"y\"}]")) {
            responses.clear();
            respond(200, body);
            assertThrows(GeocodingUnavailableException.class,
                    () -> geocoder.search("Kilimani " + body.length(), "KE"), body);
        }
    }

    @Test
    @DisplayName("back-to-back lookups are spaced out to respect the one-per-second limit")
    void throttlesRequests() throws Exception {
        respond(200, KILIMANI);
        respond(200, KILIMANI);
        respond(200, KILIMANI);

        geocoder.search("Kilimani", "KE");
        assertTrue(sleeps.isEmpty(), "the first request needs no wait");

        geocoder.search("Westlands", "KE");
        assertEquals(List.of(NominatimGeocoder.MIN_REQUEST_INTERVAL_MILLIS), sleeps);

        now.addAndGet(5_000);
        geocoder.search("Lavington", "KE");
        assertEquals(1, sleeps.size(), "no wait once enough time has passed");
    }

    @Test
    @DisplayName("an interrupt while waiting is reported as unavailable and keeps the interrupt flag")
    void interruptWhileThrottled() throws Exception {
        NominatimGeocoder interrupted = new NominatimGeocoder(
                uri -> new Transport.Response(200, KILIMANI),
                millis -> {
                    throw new InterruptedException();
                },
                now::get);

        interrupted.search("Kilimani", "KE"); // first request: no wait needed
        assertThrows(GeocodingUnavailableException.class, () -> interrupted.search("Westlands", "KE"));
        assertTrue(Thread.interrupted(), "interrupt flag should be restored");
    }
}
