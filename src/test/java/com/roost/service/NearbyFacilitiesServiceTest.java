package com.roost.service;

import com.roost.service.NearbyFacilitiesService.Facility;
import com.roost.service.NearbyFacilitiesService.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises retry/backoff, the failure-vs-genuinely-empty distinction,
 * Overpass's HTTP-200 "runtime error" case, and the cache -- all offline,
 * via a scripted fake Transport and a recording Sleeper.
 */
class NearbyFacilitiesServiceTest {

    private static final double LAT = -1.286389;
    private static final double LNG = 36.817223;

    private static final String THREE_CATEGORIES = """
            {"elements":[
              {"type":"way","center":{"lat":-1.2800,"lon":36.8200},
               "tags":{"highway":"trunk","name":"Thika Superhighway"}},
              {"type":"node","lat":-1.2900,"lon":36.8200,
               "tags":{"shop":"mall","name":"TRM Mall"}},
              {"type":"way","center":{"lat":-1.3000,"lon":36.8300},
               "tags":{"amenity":"hospital","name":"Aga Khan Hospital"}},
              {"type":"node","lat":-1.2864,"lon":36.8173,
               "tags":{"shop":"mall"}}
            ]}""";

    /** Plays back scripted outcomes: a Response, or an Exception to throw. */
    private static class ScriptedTransport implements Transport {
        private final Deque<Object> script = new ArrayDeque<>();
        int calls = 0;

        ScriptedTransport then(int status, String body) {
            script.add(new Response(status, body));
            return this;
        }

        ScriptedTransport thenThrow(Exception e) {
            script.add(e);
            return this;
        }

        @Override
        public Response post(String formBody) throws Exception {
            calls++;
            Object next = script.poll();
            if (next == null) throw new IllegalStateException("script exhausted (call " + calls + ")");
            if (next instanceof Exception e) throw e;
            return (Response) next;
        }
    }

    private final List<Long> sleeps = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private ScriptedTransport transport;

    @BeforeEach
    void setUp() {
        transport = new ScriptedTransport();
        sleeps.clear();
    }

    private NearbyFacilitiesService service() {
        return new NearbyFacilitiesService(transport, sleeps::add, clock::get);
    }

    @Test
    @DisplayName("returns the closest of each category, nearest first, skipping unnamed features")
    void parsesClosestPerCategory() {
        transport.then(200, THREE_CATEGORIES);

        Optional<List<Facility>> result = service().lookup(LAT, LNG);

        assertTrue(result.isPresent());
        List<Facility> facilities = result.get();
        // Nearest-first by construction: mall ~506m, road ~775m, hospital ~2072m
        // from LAT/LNG given the coordinates in THREE_CATEGORIES.
        assertEquals(List.of("mall", "road", "hospital"),
                facilities.stream().map(Facility::category).toList());
        assertTrue(facilities.get(0).distanceMeters() <= facilities.get(1).distanceMeters());
        assertTrue(facilities.get(1).distanceMeters() <= facilities.get(2).distanceMeters());
        assertEquals("TRM Mall", facilities.get(0).name());
        assertEquals(1, transport.calls);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("a genuine empty answer is Optional.of(empty), NOT a failure")
    void genuineEmptyIsNotFailure() {
        transport.then(200, "{\"elements\":[]}");

        Optional<List<Facility>> result = service().lookup(LAT, LNG);

        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    @DisplayName("retries HTTP 503 with 1s then 2s backoff, then succeeds")
    void retriesTransientStatusThenSucceeds() {
        transport.then(503, "busy").then(429, "slow down").then(200, THREE_CATEGORIES);

        Optional<List<Facility>> result = service().lookup(LAT, LNG);

        assertTrue(result.isPresent());
        assertEquals(3, transport.calls);
        assertEquals(List.of(1000L, 2000L), sleeps);
    }

    @Test
    @DisplayName("gives up after 3 attempts and reports failure (Optional.empty)")
    void givesUpAfterMaxAttempts() {
        transport.then(503, "").then(503, "").then(503, "");

        Optional<List<Facility>> result = service().lookup(LAT, LNG);

        assertTrue(result.isEmpty());
        assertEquals(NearbyFacilitiesService.MAX_ATTEMPTS, transport.calls);
        assertEquals(List.of(1000L, 2000L), sleeps); // no sleep after the final attempt
    }

    @Test
    @DisplayName("does not retry a non-retryable status like 400")
    void doesNotRetryClientError() {
        transport.then(400, "bad query");

        assertTrue(service().lookup(LAT, LNG).isEmpty());
        assertEquals(1, transport.calls);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("retries I/O failures")
    void retriesIoException() {
        transport.thenThrow(new IOException("connection reset")).then(200, THREE_CATEGORIES);

        assertTrue(service().lookup(LAT, LNG).isPresent());
        assertEquals(2, transport.calls);
    }

    @Test
    @DisplayName("HTTP 200 carrying an Overpass 'runtime error' is a failure, not an empty result")
    void runtimeErrorInsideHttp200IsRetried() {
        String timedOut = "{\"elements\":[],\"remark\":\"runtime error: Query timed out in \\\"query\\\" at line 1\"}";
        transport.then(200, timedOut).then(200, THREE_CATEGORIES);

        Optional<List<Facility>> result = service().lookup(LAT, LNG);

        assertTrue(result.isPresent());
        assertEquals(3, result.get().size()); // the good second answer, not the bogus empty one
        assertEquals(2, transport.calls);
    }

    @Test
    @DisplayName("unparseable bodies count as failures")
    void garbageBodyIsFailure() {
        transport.then(200, "<html>oops</html>").then(200, "not json").then(200, "{}");

        assertTrue(service().lookup(LAT, LNG).isEmpty());
        assertEquals(3, transport.calls);
    }

    @Test
    @DisplayName("successful lookups are cached by ~11m-rounded coordinates")
    void cachesSuccessfulLookups() {
        transport.then(200, THREE_CATEGORIES);
        NearbyFacilitiesService service = service();

        Optional<List<Facility>> first = service.lookup(-1.28610, 36.81720);
        Optional<List<Facility>> second = service.lookup(-1.28612, 36.81722); // same 4dp key

        assertEquals(first, second);
        assertEquals(1, transport.calls);
    }

    @Test
    @DisplayName("a different location is not served from the cache")
    void differentLocationMisses() {
        transport.then(200, THREE_CATEGORIES).then(200, "{\"elements\":[]}");
        NearbyFacilitiesService service = service();

        service.lookup(LAT, LNG);
        Optional<List<Facility>> other = service.lookup(-1.2000, 36.9000);

        assertEquals(2, transport.calls);
        assertTrue(other.orElseThrow().isEmpty());
    }

    @Test
    @DisplayName("cache entries expire after 24 hours")
    void cacheExpires() {
        transport.then(200, THREE_CATEGORIES).then(200, THREE_CATEGORIES);
        NearbyFacilitiesService service = service();

        service.lookup(LAT, LNG);
        clock.addAndGet(23L * 60 * 60 * 1000);
        service.lookup(LAT, LNG);
        assertEquals(1, transport.calls, "still fresh at 23h");

        clock.addAndGet(2L * 60 * 60 * 1000);
        service.lookup(LAT, LNG);
        assertEquals(2, transport.calls, "expired at 25h");
    }

    @Test
    @DisplayName("failures are never cached, so the next call tries again")
    void failuresNotCached() {
        transport.then(503, "").then(503, "").then(503, "").then(200, THREE_CATEGORIES);
        NearbyFacilitiesService service = service();

        assertTrue(service.lookup(LAT, LNG).isEmpty());
        assertTrue(service.lookup(LAT, LNG).isPresent());
        assertEquals(4, transport.calls);
    }

    @Test
    @DisplayName("findNearby keeps its old contract: empty list on failure, never throws")
    void findNearbyCollapsesFailureToEmptyList() {
        transport.then(503, "").then(503, "").then(503, "");

        assertEquals(List.of(), service().findNearby(LAT, LNG));
    }

    @Test
    @DisplayName("an interrupt during backoff stops retrying and preserves the interrupt flag")
    void interruptDuringBackoff() {
        transport.then(503, "");
        NearbyFacilitiesService service = new NearbyFacilitiesService(transport, millis -> {
            throw new InterruptedException("shutdown");
        }, clock::get);

        try {
            assertTrue(service.lookup(LAT, LNG).isEmpty());
            assertEquals(1, transport.calls);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // clear so later tests aren't affected
        }
    }
}
