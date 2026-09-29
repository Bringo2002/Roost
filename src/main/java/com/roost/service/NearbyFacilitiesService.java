package com.roost.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roost.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Looks up nearby points of interest -- the closest shopping mall,
 * hospital, and major road -- around a set of coordinates, using
 * OpenStreetMap's free Overpass API. No API key, no billing account.
 * Unlike Google Places, roads are a natively well-supported query here
 * since OSM tags them explicitly by class (motorway/trunk/primary/...),
 * which is what makes "200m from Thika Superhighway" possible at all.
 */
@Service
public class NearbyFacilitiesService {

    private static final Logger log = LoggerFactory.getLogger(NearbyFacilitiesService.class);

    private static final String OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter";
    private static final int SEARCH_RADIUS_METERS = 3000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** Total tries per lookup (1 initial + 2 retries). */
    static final int MAX_ATTEMPTS = 3;
    /** Wait before retry n is BASE * 2^(n-1): 1s, then 2s. */
    static final long BACKOFF_BASE_MILLIS = 1_000;
    /** Overpass answers 429 when rate limiting and 502/503/504 when overloaded. */
    private static final java.util.Set<Integer> RETRYABLE_STATUSES = java.util.Set.of(429, 502, 503, 504);

    /**
     * Results are cached by coordinates rounded to 4 decimal places (~11 m),
     * for a day: nothing near a building changes faster than that, and
     * listings in the same building/compound (or a re-verification) then
     * cost no Overpass call at all. Distances in a cached result are
     * measured from the first point that populated it, so they can be off
     * by up to roughly 11 m for a neighbouring listing -- irrelevant next to
     * the "600m from X" precision shown to users.
     */
    private static final long CACHE_TTL_MILLIS = Duration.ofHours(24).toMillis();
    private static final int CACHE_MAX_ENTRIES = 500;

    /** One HTTP round trip to Overpass; a seam so retry logic is testable offline. */
    interface Transport {
        Response post(String formBody) throws Exception;

        record Response(int status, String body) {}
    }

    /** Backoff sleeper; a seam so tests don't actually wait. */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private record CacheEntry(List<Facility> facilities, long expiresAtMillis) {}

    private final Transport transport;
    private final Sleeper sleeper;
    private final LongSupplier clockMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Access-ordered LRU, guarded by "this" (lookups are rare and short).
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    public record Facility(String name, String category, double distanceMeters, double lat, double lng) {}

    @Autowired
    public NearbyFacilitiesService() {
        this(overpassTransport(), Thread::sleep, System::currentTimeMillis);
    }

    NearbyFacilitiesService(Transport transport, Sleeper sleeper, LongSupplier clockMillis) {
        this.transport = transport;
        this.sleeper = sleeper;
        this.clockMillis = clockMillis;
    }

    private static Transport overpassTransport() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        return formBody -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OVERPASS_ENDPOINT))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new Transport.Response(response.statusCode(), response.body());
        };
    }

    /**
     * Like [lookup], but collapses failure into an empty list. Kept for
     * callers that can't tell "nothing nearby" from "Overpass was down"
     * anyway; new code should use [lookup] so a failed lookup is never
     * stored as if it were a real, empty answer.
     */
    public List<Facility> findNearby(double lat, double lng) {
        return lookup(lat, lng).orElse(List.of());
    }

    /**
     * Returns the single closest mall, hospital, and major road to
     * [lat]/[lng] (omitting any category with nothing found within
     * range), sorted nearest-first, or Optional.empty() if Overpass could
     * not give a trustworthy answer.
     *
     * The distinction matters: Optional.of(emptyList) means "looked, and
     * there is genuinely nothing within 3 km" and is safe to store;
     * Optional.empty() means "don't know" and must NOT be stored, or the
     * listing would show no facilities forever after one bad request.
     *
     * Never throws. Transient failures (timeouts, connection errors, HTTP
     * 429/502/503/504, and Overpass's HTTP-200-but-"runtime error" query
     * timeouts) are retried with exponential backoff; only successful
     * answers are cached. Blocks the calling thread while retrying, so call
     * it off the request thread (see NearbyFacilitiesUpdateListener).
     */
    public Optional<List<Facility>> lookup(double lat, double lng) {
        String key = cacheKey(lat, lng);
        List<Facility> cached = cacheGet(key);
        if (cached != null) return Optional.of(cached);

        String body = "data=" + URLEncoder.encode(buildQuery(lat, lng), StandardCharsets.UTF_8);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            boolean retryable;
            try {
                Transport.Response response = transport.post(body);
                if (response.status() == 200) {
                    Optional<List<Facility>> parsed = parseResponse(response.body(), lat, lng);
                    if (parsed.isPresent()) {
                        cachePut(key, parsed.get());
                        return parsed;
                    }
                    retryable = true; // 200 carrying an Overpass runtime error
                } else {
                    retryable = RETRYABLE_STATUSES.contains(response.status());
                    if (!retryable) {
                        log.warn("Overpass returned non-retryable HTTP {}", response.status());
                        return Optional.empty();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception e) {
                retryable = true; // timeout / connection reset / bad JSON
                log.debug("Overpass attempt {} failed: {}", attempt, e.toString());
            }

            if (retryable && attempt < MAX_ATTEMPTS) {
                try {
                    sleeper.sleep(BACKOFF_BASE_MILLIS << (attempt - 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
        log.warn("Overpass lookup failed after {} attempts for ({}, {})", MAX_ATTEMPTS, lat, lng);
        return Optional.empty();
    }

    private static String cacheKey(double lat, double lng) {
        return String.format(Locale.ROOT, "%.4f,%.4f", lat, lng);
    }

    private synchronized List<Facility> cacheGet(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.expiresAtMillis() <= clockMillis.getAsLong()) {
            cache.remove(key);
            return null;
        }
        return entry.facilities();
    }

    private synchronized void cachePut(String key, List<Facility> facilities) {
        cache.put(key, new CacheEntry(List.copyOf(facilities), clockMillis.getAsLong() + CACHE_TTL_MILLIS));
    }

    /** Empty when the body is unusable or Overpass reported a runtime error. */
    private Optional<List<Facility>> parseResponse(String body, double lat, double lng) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String remark = root.path("remark").asText("");
            if (remark.startsWith("runtime error")) {
                log.debug("Overpass runtime error: {}", remark);
                return Optional.empty();
            }
            if (!root.has("elements")) return Optional.empty();
            return Optional.of(parseClosestPerCategory(root.path("elements"), lat, lng));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String buildQuery(double lat, double lng) {
        return String.format(Locale.ROOT,
                "[out:json][timeout:10];(" +
                "node[\"shop\"=\"mall\"](around:%d,%f,%f);" +
                "way[\"shop\"=\"mall\"](around:%d,%f,%f);" +
                "node[\"amenity\"=\"hospital\"](around:%d,%f,%f);" +
                "way[\"amenity\"=\"hospital\"](around:%d,%f,%f);" +
                "way[\"highway\"~\"^(motorway|trunk|primary)$\"](around:%d,%f,%f);" +
                ");out center tags;",
                SEARCH_RADIUS_METERS, lat, lng,
                SEARCH_RADIUS_METERS, lat, lng,
                SEARCH_RADIUS_METERS, lat, lng,
                SEARCH_RADIUS_METERS, lat, lng,
                SEARCH_RADIUS_METERS, lat, lng);
    }

    private List<Facility> parseClosestPerCategory(JsonNode elements, double originLat, double originLng) {

        Facility closestMall = null;
        Facility closestHospital = null;
        Facility closestRoad = null;

        for (JsonNode el : elements) {
            JsonNode tags = el.path("tags");
            String name = tags.path("name").isMissingNode() ? null : tags.path("name").asText();
            if (name == null || name.isBlank()) continue; // skip unnamed features -- not useful to show

            Double lat = null;
            Double lng = null;
            if (el.has("lat") && el.has("lon")) {
                lat = el.path("lat").asDouble();
                lng = el.path("lon").asDouble();
            } else if (el.has("center")) {
                lat = el.path("center").path("lat").asDouble();
                lng = el.path("center").path("lon").asDouble();
            }
            if (lat == null || lng == null) continue;

            double distance = GeoUtils.haversineMeters(originLat, originLng, lat, lng);

            if ("mall".equals(textOrNull(tags, "shop"))) {
                if (closestMall == null || distance < closestMall.distanceMeters()) {
                    closestMall = new Facility(name, "mall", distance, lat, lng);
                }
            } else if ("hospital".equals(textOrNull(tags, "amenity"))) {
                if (closestHospital == null || distance < closestHospital.distanceMeters()) {
                    closestHospital = new Facility(name, "hospital", distance, lat, lng);
                }
            } else if (tags.has("highway")) {
                if (closestRoad == null || distance < closestRoad.distanceMeters()) {
                    closestRoad = new Facility(name, "road", distance, lat, lng);
                }
            }
        }

        List<Facility> results = new ArrayList<>();
        if (closestMall != null) results.add(closestMall);
        if (closestHospital != null) results.add(closestHospital);
        if (closestRoad != null) results.add(closestRoad);
        results.sort(Comparator.comparingDouble(Facility::distanceMeters));
        return results;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() ? null : value.asText();
    }
}
