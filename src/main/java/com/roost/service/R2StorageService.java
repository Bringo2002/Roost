package com.roost.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;

/**
 * Stores and retrieves opaque encrypted attachment bytes in Cloudflare R2
 * (S3-compatible object storage). The server only ever handles ciphertext
 * here -- files are encrypted client-side before upload and decrypted
 * client-side after download, so R2 (like the database before it) never
 * has the keys needed to read them.
 *
 * Configured via env vars: R2_ACCOUNT_ID, R2_ACCESS_KEY_ID,
 * R2_SECRET_ACCESS_KEY, R2_BUCKET. If any are unset, the service stays
 * inert (rather than failing app startup) and throws a clear error only
 * when an upload/download is actually attempted -- so a missing R2 config
 * doesn't take down login, browsing, or text-only messaging.
 */
@Service
public class R2StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final boolean configured;

    public R2StorageService(
            @Value("${r2.account-id:}") String accountId,
            @Value("${r2.access-key-id:}") String accessKeyId,
            @Value("${r2.secret-access-key:}") String secretAccessKey,
            @Value("${r2.bucket:}") String bucket
    ) {
        this.bucket = bucket;
        this.configured = !accountId.isBlank() && !accessKeyId.isBlank()
                && !secretAccessKey.isBlank() && !bucket.isBlank();

        this.s3Client = configured
                ? S3Client.builder()
                        .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                        .region(Region.of("auto"))
                        .serviceConfiguration(S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build())
                        .build()
                : null;
    }

    /** Uploads opaque bytes under a fresh random key and returns that key. */
    public String upload(byte[] data) {
        requireConfigured();
        String key = "attachments/" + UUID.randomUUID();
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromBytes(data)
        );
        return key;
    }

    public byte[] download(String key) {
        requireConfigured();
        try (var stream = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read attachment from R2", e);
        }
    }

    public void delete(String key) {
        requireConfigured();
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException(
                    "R2 storage is not configured. Set R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, " +
                    "R2_SECRET_ACCESS_KEY, and R2_BUCKET environment variables."
            );
        }
    }
}
