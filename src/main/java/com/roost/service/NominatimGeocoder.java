package com.roost.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * {@link Geocoder} backed by OpenStreetMap's public Nominatim service: free,
 * no API key. Written to stay within its usage policy, which asks for at most
 * one request per second, an identifying User-Agent, and cached results. Roost
 * only geocodes when a lister verifies a location, so volume is tiny.
 *
 * <p>Failures are never cached, so a brief outage can't turn into a stored
 * "address not found".
 */
@Service
public class NominatimGeocoder implements Geocoder {

    private static final String ENDPOINT = "https://nominatim.openstreetmap.org/search";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String USER_AGENT = "roost-backend/1.0 (+https://github.com/Bringo2002/Roost)";

    /** The public service allows one request a second; leave some headroom. */
    static final long MIN_REQUEST_INTERVAL_MILLIS = 1_100;
    private static final int RESULT_LIMIT = 5;

    /** Place boundaries rarely change, so a found place is kept for a week. */
    private static final long FOUND_TTL_MILLIS = Duration.ofDays(7).toMillis();
    /** A miss is kept only briefly, in case the map data was just corrected. */
    private static final long NOT_FOUND_TTL_MILLIS = Duration.ofHours(1).toMillis();
    private static final int CACHE_MAX_ENTRIES = 1_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One HTTP round trip to Nominatim; a seam so the logic is testable offline. */
    interface Transport {
        Response get(URI uri) throws Exception;

        record Response(int status, String body) {
        }
    }

    /** Throttle sleeper; a seam so tests don't actually wait. */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private record CacheEntry(List<GeocodedPlace> places, long expiresAtMillis) {
    }

    private final Transport transport;
    private final Sleeper sleeper;
    private final LongSupplier clockMillis;

    // Access-ordered LRU, guarded by "this" (lookups are rare and short).
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    // Separate from the cache's lock so a request waiting out the rate limit
    // never holds up cache hits.
    private final Object throttleLock = new Object();
    private long lastRequestAtMillis = Long.MIN_VALUE / 2;

    @Autowired
    public NominatimGeocoder() {
        this(nominatimTransport(), Thread::sleep, System::currentTimeMillis);
    }

    NominatimGeocoder(Transport transport, Sleeper sleeper, LongSupplier clockMillis) {
        this.transport = transport;
        this.sleeper = sleeper;
        this.clockMillis = clockMillis;
    }

    private static Transport nominatimTransport() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new Transport.Response(response.statusCode(), response.body());
        };
    }

    @Override
    public List<GeocodedPlace> search(String query, String countryCode) throws GeocodingUnavailableException {
        String normalised = normalise(query);
        if (normalised.isEmpty()) {
            return List.of();
        }
        String country = countryCode == null ? "" : countryCode.trim().toLowerCase(Locale.ROOT);
        String key = country + "|" + normalised;

        List<GeocodedPlace> cached = cacheGet(key);
        if (cached != null) {
            return cached;
        }
        List<GeocodedPlace> places = fetch(normalised, country);
        cachePut(key, places);
        return places;
    }

    private List<GeocodedPlace> fetch(String query, String country) throws GeocodingUnavailableException {
        URI uri = buildUri(query, country);
        try {
            awaitTurn();
            Transport.Response response = transport.get(uri);
            if (response.status() != 200) {
                throw new GeocodingUnavailableException("Nominatim answered HTTP " + response.status());
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeocodingUnavailableException("Interrupted while geocoding", e);
        } catch (GeocodingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new GeocodingUnavailableException("Nominatim request failed", e);
        }
    }

    /** Blocks just long enough to keep requests at least the minimum interval apart. */
    private void awaitTurn() throws InterruptedException {
        synchronized (throttleLock) {
            long wait = lastRequestAtMillis + MIN_REQUEST_INTERVAL_MILLIS - clockMillis.getAsLong();
            if (wait > 0) {
                sleeper.sleep(wait);
            }
            lastRequestAtMillis = clockMillis.getAsLong();
        }
    }

    private static URI buildUri(String query, String country) {
        StringBuilder url = new StringBuilder(ENDPOINT)
                .append("?format=jsonv2&dedupe=1&limit=").append(RESULT_LIMIT)
                .append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (!country.isEmpty()) {
            url.append("&countrycodes=").append(URLEncoder.encode(country, StandardCharsets.UTF_8));
        }
        return URI.create(url.toString());
    }

    /**
     * Reads Nominatim's {@code jsonv2} search response. An empty array is a
     * genuine "no match". A response with entries but none readable is treated
     * as a failure instead, so a format change can't masquerade as "address
     * not found".
     */
    static List<GeocodedPlace> parse(String body) throws GeocodingUnavailableException {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw new GeocodingUnavailableException("Nominatim returned unreadable JSON", e);
        }
        if (root == null || !root.isArray()) {
            throw new GeocodingUnavailableException("Unexpected response from Nominatim");
        }
        List<GeocodedPlace> places = new ArrayList<>();
        for (JsonNode node : root) {
            GeocodedPlace place = toPlace(node);
            if (place != null) {
                places.add(place);
            }
        }
        if (root.size() > 0 && places.isEmpty()) {
            throw new GeocodingUnavailableException("Nominatim results could not be read");
        }
        return List.copyOf(places);
    }

    private static GeocodedPlace toPlace(JsonNode node) {
        try {
            JsonNode box = node.path("boundingbox");
            if (!box.isArray() || box.size() != 4) {
                return null;
            }
            // Nominatim orders the box as [south, north, west, east].
            double south = Double.parseDouble(box.get(0).asText());
            double north = Double.parseDouble(box.get(1).asText());
            double west = Double.parseDouble(box.get(2).asText());
            double east = Double.parseDouble(box.get(3).asText());
            double lat = Double.parseDouble(node.path("lat").asText());
            double lon = Double.parseDouble(node.path("lon").asText());
            if (!allFinite(south, north, west, east, lat, lon)) {
                return null;
            }
            return new GeocodedPlace(node.path("display_name").asText(""), lat, lon, south, north, west, east);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean allFinite(double... values) {
        for (double v : values) {
            if (!Double.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    private static String normalise(String query) {
        return query == null ? "" : query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private synchronized List<GeocodedPlace> cacheGet(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMillis() <= clockMillis.getAsLong()) {
            cache.remove(key);
            return null;
        }
        return entry.places();
    }

    private synchronized void cachePut(String key, List<GeocodedPlace> places) {
        long ttl = places.isEmpty() ? NOT_FOUND_TTL_MILLIS : FOUND_TTL_MILLIS;
        cache.put(key, new CacheEntry(places, clockMillis.getAsLong() + ttl));
    }
}
