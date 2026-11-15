package com.roost.service;

import com.roost.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivateDocumentStorageTest {

    private static final String BUCKET = "roost-private-test";
    private static final VerificationFilePolicy.Accepted JPEG = new VerificationFilePolicy.Accepted("image/jpeg", "jpg");

    private S3Client s3;
    private PrivateDocumentStorage storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        storage = new PrivateDocumentStorage(s3, BUCKET);
    }

    @Test
    @DisplayName("store writes an encrypted object under verification/ with a random key and the detected type")
    void storeWritesEncryptedObject() {
        String key = storage.store(new byte[] {1, 2, 3}, JPEG);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals(key, request.getValue().key());
        assertEquals("image/jpeg", request.getValue().contentType());
        assertEquals(ServerSideEncryption.AES256, request.getValue().serverSideEncryption());
        assertTrue(key.matches("^verification/[0-9a-f-]{36}\\.jpg$"), key);
    }

    @Test
    @DisplayName("two stored files never share a key")
    void keysAreUnique() {
        assertFalse(storage.store(new byte[] {1}, JPEG).equals(storage.store(new byte[] {1}, JPEG)));
    }

    @Test
    @DisplayName("load returns the stored bytes")
    void loadReturnsBytes() {
        String key = storage.store(new byte[] {9}, JPEG);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {9, 8, 7}));

        assertArrayEquals(new byte[] {9, 8, 7}, storage.load(key));
    }

    @Test
    @DisplayName("delete removes that one key from the private bucket")
    void deleteRemovesKey() {
        String key = storage.store(new byte[] {9}, JPEG);

        storage.delete(key);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals(key, request.getValue().key());
    }

    @Test
    @DisplayName("keys that this service did not produce are refused before reaching S3")
    void refusesForeignKeys() {
        for (String bad : new String[] {
                "../secret.jpg", "properties/photo.jpg", "verification/../x.jpg",
                "verification/not-a-uuid.jpg", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> storage.load(bad), String.valueOf(bad));
            assertThrows(IllegalArgumentException.class, () -> storage.delete(bad), String.valueOf(bad));
        }
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("unconfigured storage reports 503 on every operation")
    void unconfiguredFailsWith503() {
        PrivateDocumentStorage unconfigured = new PrivateDocumentStorage("", "", "", "eu-north-1");

        assertFalse(unconfigured.isConfigured());
        ApiException ex = assertThrows(ApiException.class, () -> unconfigured.store(new byte[] {1}, JPEG));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ApiException.class, () -> unconfigured.load("verification/x")).getStatus());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ApiException.class, () -> unconfigured.delete("verification/x")).getStatus());
    }

    @Test
    @DisplayName("a bucket without keys stays unconfigured, not silently using ambient credentials")
    void bucketWithoutKeysIsUnconfigured() {
        assertFalse(new PrivateDocumentStorage(BUCKET, "", "", "eu-north-1").isConfigured());
    }

    @Test
    @DisplayName("keys without a bucket stay unconfigured")
    void keysWithoutBucketAreUnconfigured() {
        assertFalse(new PrivateDocumentStorage("", "AKIAEXAMPLE", "secret", "eu-north-1").isConfigured());
    }

    @Test
    @DisplayName("a half-set key pair fails at startup")
    void halfSetKeyPairFailsAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrivateDocumentStorage(BUCKET, "AKIAEXAMPLE", "", "eu-north-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new PrivateDocumentStorage(BUCKET, "", "secret", "eu-north-1"));
    }

    @Test
    @DisplayName("bucket, keys and region together configure the service")
    void fullySetIsConfigured() {
        assertTrue(new PrivateDocumentStorage(BUCKET, "AKIAEXAMPLE", "secret", "eu-north-1").isConfigured());
    }
}
