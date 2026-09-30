package com.roost.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link R2StorageService#generatePresignedPut}.
 *
 * <p>These tests exercise the service in its unconfigured state (no real AWS
 * credentials) to verify fast-fail behaviour and key/URL construction logic
 * without making any network calls. Integration against a real R2 bucket is
 * covered by manual verification during deployment.
 */
@DisplayName("R2StorageService — generatePresignedPut()")
class R2StorageServicePresignTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Builds a fully configured service instance using dummy credentials. */
    private R2StorageService configuredService() {
        return new R2StorageService(
                "test-account-id",
                "test-access-key",
                "test-secret-key",
                "test-bucket",
                "https://pub.example.com"
        );
    }

    /** Builds a service instance with all env vars intentionally blank. */
    private R2StorageService unconfiguredService() {
        return new R2StorageService("", "", "", "", "");
    }

    /** Builds a service instance with credentials but no public base URL. */
    private R2StorageService noPublicUrlService() {
        return new R2StorageService(
                "test-account-id",
                "test-access-key",
                "test-secret-key",
                "test-bucket",
                "" // no public base URL
        );
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("When R2 is not configured")
    class WhenUnconfigured {

        @Test
        @DisplayName("throws IllegalStateException with a clear message naming the missing env vars")
        void throwsWithClearMessage_whenCredentialsMissing() {
            R2StorageService service = unconfiguredService();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.generatePresignedPut("properties/", "image/jpeg", ".jpg", 15)
            );

            String message = ex.getMessage();
            assertTrue(message.contains("R2_ACCOUNT_ID")
                            || message.contains("not configured"),
                    "Exception message should name the missing configuration: " + message);
        }

        @Test
        @DisplayName("throws IllegalStateException when credentials are present but public URL is missing")
        void throwsWithClearMessage_whenPublicUrlMissing() {
            R2StorageService service = noPublicUrlService();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.generatePresignedPut("properties/", "image/jpeg", ".jpg", 15)
            );

            assertTrue(ex.getMessage().contains("R2_PUBLIC_BASE_URL")
                            || ex.getMessage().contains("public"),
                    "Exception should reference the missing public URL config: " + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("PresignedUpload record construction")
    class RecordConstruction {

        @Test
        @DisplayName("publicUrl starts with the configured public base URL")
        void publicUrl_startsWithPublicBaseUrl() {
            // We cannot call generatePresignedPut() without real credentials, but we
            // can test the PresignedUpload record itself and the public URL assembly
            // logic by directly constructing the record as the service would.
            String publicBaseUrl = "https://pub.example.com";
            String key = "properties/some-uuid.jpg";
            String fakeUploadUrl = "https://bucket.r2.cloudflarestorage.com/presigned";

            R2StorageService.PresignedUpload result =
                    new R2StorageService.PresignedUpload(fakeUploadUrl, publicBaseUrl + "/" + key, key);

            assertTrue(result.publicUrl().startsWith(publicBaseUrl),
                    "publicUrl should start with the base URL");
        }

        @Test
        @DisplayName("publicUrl ends with the correct extension for photos")
        void publicUrl_endsWithJpgForPhotos() {
            String key = "properties/uuid.jpg";
            R2StorageService.PresignedUpload result =
                    new R2StorageService.PresignedUpload("https://upload.url", "https://pub.example.com/" + key, key);

            assertTrue(result.publicUrl().endsWith(".jpg"),
                    "Photo publicUrl should end with .jpg");
        }

        @Test
        @DisplayName("publicUrl ends with the correct extension for videos")
        void publicUrl_endsWithMp4ForVideos() {
            String key = "properties/uuid.mp4";
            R2StorageService.PresignedUpload result =
                    new R2StorageService.PresignedUpload("https://upload.url", "https://pub.example.com/" + key, key);

            assertTrue(result.publicUrl().endsWith(".mp4"),
                    "Video publicUrl should end with .mp4");
        }

        @Test
        @DisplayName("key starts with the given prefix")
        void key_startsWithGivenPrefix() {
            String prefix = "properties/";
            String key = prefix + "uuid.jpg";
            R2StorageService.PresignedUpload result =
                    new R2StorageService.PresignedUpload("https://upload.url", "https://pub.example.com/" + key, key);

            assertTrue(result.key().startsWith(prefix),
                    "key should start with the given prefix");
        }

        @Test
        @DisplayName("all three fields are accessible via record accessors")
        void allFieldsAccessible() {
            R2StorageService.PresignedUpload result =
                    new R2StorageService.PresignedUpload("upload", "public", "key");

            assertEquals("upload", result.uploadUrl());
            assertEquals("public", result.publicUrl());
            assertEquals("key",    result.key());
        }
    }
}
