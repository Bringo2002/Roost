package com.roost.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initializes the shared FirebaseApp at startup, parsing the service-account
 * JSON with Jackson (already on the classpath via Spring Boot) instead of
 * going through GoogleCredentials.fromStream() which relies on Gson and
 * breaks with Gson 2.11+ strict-mode defaults.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = Logger.getLogger(FirebaseConfig.class.getName());

    public FirebaseConfig(@Value("${firebase.service-account-json:}") String serviceAccountJson) {
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warning("Firebase disabled: no FIREBASE_SERVICE_ACCOUNT_JSON configured");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) return;

        try {
            String json = serviceAccountJson.startsWith("/")
                    ? Files.readString(Path.of(serviceAccountJson))
                    : serviceAccountJson;

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);

            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromPkcs8(
                    node.get("client_id").asText(""),
                    node.get("client_email").asText(),
                    node.get("private_key").asText(),
                    node.get("private_key_id").asText(),
                    /* scopes */ java.util.List.of(
                            "https://www.googleapis.com/auth/firebase",
                            "https://www.googleapis.com/auth/cloud-platform"
                    )
            );

            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(node.get("project_id").asText())
                    .build());
            log.info("Firebase initialized for project: " + node.get("project_id").asText());
        } catch (IOException e) {
            log.log(Level.WARNING, "Firebase init failed", e);
        }
    }
}
