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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side Gemini document fraud-check for landlord verification
 * documents (title deeds, IDs, utility bills, lease agreements).
 *
 * This used to run directly on the Flutter client with the Gemini API
 * key baked into the app via --dart-define. That key is trivially
 * extractable from a compiled release APK/IPA (basic decompilation or
 * even `strings` on the binary), letting anyone spend against Roost's
 * Gemini quota independent of the app's own logic. Moving the call here
 * means the key never leaves the server.
 *
 * This is advisory only, same as before the move: see
 * PropertyService.setVerified, which computes the real "Verified
 * Listing" trust flag from phoneVerified/gpsVerified/photoApproved and
 * never reads anything this service returns. Photo/document approval
 * itself still requires a human ADMIN via AdminController -- this just
 * gives the landlord (and that admin) an immediate opinion on whether
 * the document looks genuine.
 */
@Service
public class GeminiDocVerificationService {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=%s";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiDocVerificationService(@Value("${gemini.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Runs the fraud-check and returns a JSON-ready map. The shape
     * mirrors what the Flutter client used to parse directly off
     * Gemini's raw response, so DocVerificationService.dart's own
     * parsing barely changes -- just its source.
     *
     * [landlordName] must come from the server's own User record
     * (User.getName()), never from anything the client claims -- it
     * feeds the name-mismatch check below, which would otherwise be
     * trivial to spoof by just sending whatever name matches the
     * uploaded document.
     */
    public Map<String, Object> verify(byte[] imageBytes, String declaredDocType, String landlordName) {
        if (apiKey == null || apiKey.isBlank()) {
            return pendingReview("NO_API_KEY");
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String requestBody = buildRequestBody(base64Image, declaredDocType, landlordName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(ENDPOINT_TEMPLATE, apiKey)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return pendingReview("AI_UNAVAILABLE");
            }

            return parseGeminiResponse(response.body(), landlordName);
        } catch (Exception e) {
            return pendingReview("AI_UNAVAILABLE");
        }
    }

    /**
     * Same system instruction, prompt, and response schema the client
     * used to send directly -- preserves the exact cost characteristics
     * (Flash model, cached system instruction, schema-enforced JSON
     * output) the original implementation was deliberately built around.
     */
    private String buildRequestBody(String base64Image, String declaredDocType, String landlordName) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode systemInstruction = root.putObject("systemInstruction");
        ArrayNode systemParts = systemInstruction.putArray("parts");
        systemParts.addObject().put("text",
                "You are an expert document fraud detection AI for Kenyan rental real estate.\n" +
                "Verify title deeds (Ministry of Lands green/cream), National IDs, Utility Bills (KPLC/Nairobi Water), or Lease Agreements.\n" +
                "Check for photo manipulation, font mismatch, missing seals, or name discrepancy.");

        ArrayNode contents = root.putArray("contents");
        ObjectNode userContent = contents.addObject();
        userContent.put("role", "user");
        ArrayNode userParts = userContent.putArray("parts");

        ObjectNode inlineDataPart = userParts.addObject();
        ObjectNode inlineData = inlineDataPart.putObject("inlineData");
        inlineData.put("mimeType", "image/jpeg");
        inlineData.put("data", base64Image);

        String prompt = "Declared Document Type: " + declaredDocType + "\n" +
                (landlordName != null && !landlordName.isBlank()
                        ? "Registered Account Holder Name: \"" + landlordName + "\"\n"
                        : "") +
                "Inspect this image. Return the structured JSON evaluation.";
        userParts.addObject().put("text", prompt);

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        ObjectNode schema = generationConfig.putObject("responseSchema");
        schema.put("type", "OBJECT");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("docType").put("type", "STRING").put("description", "Actual detected document type");
        properties.putObject("isAuthentic").put("type", "BOOLEAN").put("description", "True if document appears genuine");
        properties.putObject("confidence").put("type", "NUMBER").put("description", "Confidence score from 0.0 to 1.0");
        properties.putObject("extractedName").put("type", "STRING").put("description", "Full name on document or null");
        ObjectNode flagsSchema = properties.putObject("flags");
        flagsSchema.put("type", "ARRAY");
        flagsSchema.putObject("items").put("type", "STRING");
        flagsSchema.put("description", "List of fraud or anomaly flags");
        properties.putObject("summary").put("type", "STRING").put("description", "One sentence assessment");
        ArrayNode required = schema.putArray("requiredProperties");
        required.add("docType").add("isAuthentic").add("confidence").add("flags");

        return objectMapper.writeValueAsString(root);
    }

    private Map<String, Object> parseGeminiResponse(String responseBody, String landlordName) {
        try {
            JsonNode envelope = objectMapper.readTree(responseBody);
            String rawText = envelope.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText("");

            String cleaned = rawText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode result = objectMapper.readTree(cleaned);

            boolean isAuthentic = result.path("isAuthentic").asBoolean(false);
            double confidence = result.path("confidence").asDouble(0.0);
            String extractedName = result.hasNonNull("extractedName") ? result.path("extractedName").asText() : null;
            String aiDocType = result.hasNonNull("docType") ? result.path("docType").asText() : null;
            String summary = result.hasNonNull("summary") ? result.path("summary").asText() : null;

            List<String> flags = new ArrayList<>();
            if (result.has("flags") && result.get("flags").isArray()) {
                for (JsonNode f : result.get("flags")) flags.add(f.asText());
            }

            boolean nameMismatch = flags.contains("NAME_MISMATCH");
            if (!nameMismatch && landlordName != null && !landlordName.isBlank()
                    && extractedName != null && !extractedName.isBlank()) {
                nameMismatch = !fuzzyNameMatch(landlordName, extractedName);
                if (nameMismatch) flags.add("NAME_MISMATCH");
            }

            String riskLevel = (!isAuthentic || confidence < 0.4 || !flags.isEmpty()) ? "flagged" : "verified";

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("riskLevel", riskLevel);
            out.put("flags", flags);
            out.put("extractedName", extractedName);
            out.put("nameMismatch", nameMismatch);
            out.put("confidence", confidence);
            out.put("aiDocType", aiDocType);
            out.put("summary", summary);
            return out;
        } catch (Exception e) {
            return pendingReview("AI_PARSE_ERROR");
        }
    }

    private boolean fuzzyNameMatch(String nameA, String nameB) {
        Set<String> a = normalize(nameA);
        Set<String> b = normalize(nameB);
        if (a.isEmpty() || b.isEmpty()) return false;
        long intersection = a.stream().filter(b::contains).count();
        int smaller = Math.min(a.size(), b.size());
        return smaller > 0 && (double) intersection / smaller >= 0.6;
    }

    private Set<String> normalize(String s) {
        String cleaned = s.toLowerCase().replaceAll("[^a-z\\s]", "").trim();
        return new HashSet<>(List.of(cleaned.split("\\s+")));
    }

    private Map<String, Object> pendingReview(String flag) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("riskLevel", "pendingReview");
        out.put("flags", List.of(flag));
        out.put("extractedName", null);
        out.put("nameMismatch", false);
        out.put("confidence", 0.0);
        out.put("aiDocType", null);
        out.put("summary", null);
        return out;
    }
}
