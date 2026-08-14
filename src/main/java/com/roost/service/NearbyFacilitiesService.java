package com.roost.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roost.util.GeoUtils;
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
import java.util.List;
import java.util.Locale;

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

    private static final String OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter";
    private static final int SEARCH_RADIUS_METERS = 3000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record Facility(String name, String category, double distanceMeters) {}

    /**
     * Returns the single closest mall, hospital, and major road to
     * [lat]/[lng] (omitting any category with nothing found within
     * range), sorted nearest-first. Never throws -- Overpass is a free
     * public service that can be slow or briefly unavailable, and a
     * listing must still save successfully either way; callers get an
     * empty list on any failure rather than an exception.
     */
    public List<Facility> findNearby(double lat, double lng) {
        try {
            String query = buildQuery(lat, lng);
            String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OVERPASS_ENDPOINT))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return List.of();

            return parseClosestPerCategory(response.body(), lat, lng);
        } catch (Exception e) {
            return List.of();
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

    private List<Facility> parseClosestPerCategory(String json, double originLat, double originLng) throws Exception {
        JsonNode elements = objectMapper.readTree(json).path("elements");

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
                    closestMall = new Facility(name, "mall", distance);
                }
            } else if ("hospital".equals(textOrNull(tags, "amenity"))) {
                if (closestHospital == null || distance < closestHospital.distanceMeters()) {
                    closestHospital = new Facility(name, "hospital", distance);
                }
            } else if (tags.has("highway")) {
                if (closestRoad == null || distance < closestRoad.distanceMeters()) {
                    closestRoad = new Facility(name, "road", distance);
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
