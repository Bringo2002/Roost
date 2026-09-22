package com.roost.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gemini-powered fallback for search queries the client's own regex
 * parser (SearchPage._parseSearchIntent) can't handle -- price ranges
 * beyond "under N", amenities beyond "furnished", and anything typed in
 * Swahili/Sheng or mixed with English, which is realistic input for
 * Roost's Nairobi market and something a fixed keyword list will never
 * cover.
 *
 * Deliberately NOT called on every keystroke -- see
 * SearchController.parseIntent's doc comment for the invocation
 * strategy. This is a fallback path, not the primary search mechanism:
 * the free, instant regex parser handles the common case, and this
 * only runs when that parser extracts nothing and the user has
 * explicitly submitted the search (not while still typing).
 *
 * Same server-side-only pattern as GeminiDocVerificationService -- the
 * API key never leaves the backend.
 */
@Service
public class GeminiSearchIntentService {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=%s";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiSearchIntentService(@Value("${gemini.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Returns a JSON-ready map of extracted filters, or null if the
     * API key isn't configured, the request fails, or the model
     * couldn't extract anything useful. Never throws -- a failed
     * parse just means the search falls back to a plain keyword match
     * on the original query, which is always safe.
     */
    public Map<String, Object> parseIntent(String query) {
        if (apiKey == null || apiKey.isBlank() || query == null || query.isBlank()) {
            return null;
        }

        try {
            String requestBody = buildRequestBody(query);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(ENDPOINT_TEMPLATE, apiKey)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            return parseGeminiResponse(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildRequestBody(String query) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode systemInstruction = root.putObject("systemInstruction");
        ArrayNode systemParts = systemInstruction.putArray("parts");
        systemParts.addObject().put("text",
                "You extract structured search filters from rental property search queries for the "
                + "Nairobi, Kenya market. Users may write in English, Swahili, Sheng, or a mix of all "
                + "three (e.g. 'nyumba ya bedsitter Kilimani chini ya elfu ishirini' means a bedsitter "
                + "in Kilimani under 20,000). Prices are in Kenyan Shillings; 'elfu' means thousand, "
                + "as does a bare 'k' suffix. houseType must be exactly one of: BEDSITTER, STUDIO, "
                + "1BR, 2BR, 3BR+ -- or null if not mentioned. Only set a field true/non-null if the "
                + "query actually implies it; leave everything else null/false. locationHint should be "
                + "just the neighborhood or area name, nothing else. remainingKeywords should be "
                + "whatever's left that doesn't map to a structured field (e.g. a building name), or "
                + "an empty string if nothing remains.");

        ArrayNode contents = root.putArray("contents");
        ObjectNode userContent = contents.addObject();
        userContent.put("role", "user");
        userContent.putArray("parts").addObject().put("text", "Query: " + query);

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        ObjectNode schema = generationConfig.putObject("responseSchema");
        schema.put("type", "OBJECT");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("minPrice").put("type", "NUMBER");
        properties.putObject("maxPrice").put("type", "NUMBER");
        properties.putObject("houseType").put("type", "STRING");
        properties.putObject("furnished").put("type", "BOOLEAN");
        properties.putObject("parking").put("type", "BOOLEAN");
        properties.putObject("wifi").put("type", "BOOLEAN");
        properties.putObject("water").put("type", "BOOLEAN");
        properties.putObject("security").put("type", "BOOLEAN");
        properties.putObject("verifiedOnly").put("type", "BOOLEAN");
        properties.putObject("locationHint").put("type", "STRING");
        properties.putObject("remainingKeywords").put("type", "STRING");
        ArrayNode required = schema.putArray("requiredProperties");
        required.add("furnished").add("parking").add("wifi").add("water")
                .add("security").add("verifiedOnly").add("remainingKeywords");

        return objectMapper.writeValueAsString(root);
    }

    private Map<String, Object> parseGeminiResponse(String responseBody) {
        try {
            JsonNode envelope = objectMapper.readTree(responseBody);
            String rawText = envelope.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText("");

            String cleaned = rawText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode result = objectMapper.readTree(cleaned);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("minPrice", result.hasNonNull("minPrice") ? result.get("minPrice").asDouble() : null);
            out.put("maxPrice", result.hasNonNull("maxPrice") ? result.get("maxPrice").asDouble() : null);
            out.put("houseType", result.hasNonNull("houseType") ? result.get("houseType").asText() : null);
            out.put("furnished", result.path("furnished").asBoolean(false));
            out.put("parking", result.path("parking").asBoolean(false));
            out.put("wifi", result.path("wifi").asBoolean(false));
            out.put("water", result.path("water").asBoolean(false));
            out.put("security", result.path("security").asBoolean(false));
            out.put("verifiedOnly", result.path("verifiedOnly").asBoolean(false));
            out.put("locationHint", result.hasNonNull("locationHint") ? result.get("locationHint").asText() : null);
            out.put("remainingKeywords", result.path("remainingKeywords").asText(""));
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
