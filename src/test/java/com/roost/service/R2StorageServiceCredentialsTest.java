package com.roost.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how {@link R2StorageService} chooses its credentials.
 *
 * <p>The AWS default credential chain is made deterministic by publishing
 * credentials through the {@code aws.accessKeyId} / {@code aws.secretAccessKey}
 * system properties, which the chain reads before the environment, profile files
 * or instance metadata. Presigning is a local signing operation, so no network
 * calls are made. The signing access key id appears in the presigned URL's
 * {@code X-Amz-Credential} parameter, which is what the assertions inspect.
 */
@DisplayName("R2StorageService — credential selection")
class R2StorageServiceCredentialsTest {

    private static final String AWS_MARKER = "not-used-on-aws";
    private static final String BUCKET = "test-bucket";
    private static final String PUBLIC_URL = "https://pub.example.com";
    private static final String REGION = "eu-north-1";
    private static final String CHAIN_KEY_ID = "chain-key-id";

    private String previousKeyId;
    private String previousSecret;

    @BeforeEach
    void publishChainCredentials() {
        previousKeyId = System.getProperty("aws.accessKeyId");
        previousSecret = System.getProperty("aws.secretAccessKey");
        System.setProperty("aws.accessKeyId", CHAIN_KEY_ID);
        System.setProperty("aws.secretAccessKey", "chain-secret");
    }

    @AfterEach
    void restoreSystemProperties() {
        restore("aws.accessKeyId", previousKeyId);
        restore("aws.secretAccessKey", previousSecret);
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static String presignedUrl(R2StorageService service) {
        return service.generatePresignedPut("properties/", "image/jpeg", ".jpg", 15).uploadUrl();
    }

    @Test
    @DisplayName("AWS marker with blank keys signs with the default credential chain")
    void awsMarkerWithBlankKeys_usesDefaultCredentialChain() {
        R2StorageService service =
                new R2StorageService(AWS_MARKER, "", "", BUCKET, PUBLIC_URL, REGION);

        assertTrue(presignedUrl(service).contains(CHAIN_KEY_ID),
                "URL should be signed with the credentials found by the default chain");
    }

    @Test
    @DisplayName("AWS marker with explicit keys signs with those keys, not the chain")
    void awsMarkerWithExplicitKeys_usesStaticKeys() {
        R2StorageService service = new R2StorageService(
                AWS_MARKER, "static-key-id", "static-secret", BUCKET, PUBLIC_URL, REGION);

        String url = presignedUrl(service);

        assertTrue(url.contains("static-key-id"));
        assertFalse(url.contains(CHAIN_KEY_ID));
    }

    @Test
    @DisplayName("AWS marker with blank keys but no bucket stays unconfigured")
    void awsMarkerWithoutBucket_isUnconfigured() {
        R2StorageService service =
                new R2StorageService(AWS_MARKER, "", "", "", PUBLIC_URL, REGION);

        assertThrows(IllegalStateException.class, () -> presignedUrl(service));
    }

    @Test
    @DisplayName("AWS marker with only the access key id fails startup")
    void awsMarkerWithOnlyAccessKeyId_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new R2StorageService(AWS_MARKER, "only-id", "", BUCKET, PUBLIC_URL, REGION));

        assertEquals(
                "S3_ACCESS_KEY_ID and S3_SECRET_ACCESS_KEY must both be set or both be blank.",
                ex.getMessage());
    }

    @Test
    @DisplayName("AWS marker with only the secret key fails startup")
    void awsMarkerWithOnlySecret_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new R2StorageService(AWS_MARKER, "", "only-secret", BUCKET, PUBLIC_URL, REGION));
    }

    @Test
    @DisplayName("blank account id with blank keys never falls back to ambient credentials")
    void blankAccountWithBlankKeys_isUnconfigured() {
        R2StorageService service =
                new R2StorageService("", "", "", BUCKET, PUBLIC_URL, REGION);

        assertThrows(IllegalStateException.class, () -> presignedUrl(service));
    }

    @Test
    @DisplayName("R2 account id with blank keys stays unconfigured")
    void r2AccountWithBlankKeys_isUnconfigured() {
        R2StorageService service =
                new R2StorageService("test-account-id", "", "", BUCKET, PUBLIC_URL);

        assertThrows(IllegalStateException.class, () -> presignedUrl(service));
    }

    @Test
    @DisplayName("blank account id with explicit keys keeps using those keys")
    void blankAccountWithExplicitKeys_usesStaticKeys() {
        R2StorageService service = new R2StorageService(
                "", "legacy-key-id", "legacy-secret", BUCKET, PUBLIC_URL, REGION);

        String url = presignedUrl(service);

        assertTrue(url.contains("legacy-key-id"));
        assertFalse(url.contains(CHAIN_KEY_ID));
    }
}
