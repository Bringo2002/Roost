package com.roost.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;

/**
 * Stores and retrieves files in Cloudflare R2 (S3-compatible object
 * storage). Used for two distinct purposes:
 *
 *  - Encrypted chat attachments: opaque ciphertext, stored under
 *    "attachments/", only ever fetched through the authenticated
 *    download proxy -- the server never has the keys to read them.
 *  - Property photos: plain public content, stored under "properties/",
 *    returned as a direct public URL so the app can load them straight
 *    from R2/CDN without proxying every image view through this backend.
 *
 * Configured via env vars: R2_ACCOUNT_ID, R2_ACCESS_KEY_ID,
 * R2_SECRET_ACCESS_KEY, R2_BUCKET, and (for public uploads only)
 * R2_PUBLIC_BASE_URL -- the public r2.dev subdomain or custom domain
 * Cloudflare gives the bucket once public access is enabled on it. If
 * any required var is unset, the service stays inert (rather than
 * failing app startup) and throws a clear error only when an
 * upload/download is actually attempted.
 */
@Service
public class R2StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;
    private final boolean configured;
    private final boolean publicUploadConfigured;

    public R2StorageService(
            @Value("${r2.account-id:}") String accountId,
            @Value("${r2.access-key-id:}") String accessKeyId,
            @Value("${r2.secret-access-key:}") String secretAccessKey,
            @Value("${r2.bucket:}") String bucket,
            @Value("${r2.public-base-url:}") String publicBaseUrl
    ) {
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        this.configured = !accountId.isBlank() && !accessKeyId.isBlank()
                && !secretAccessKey.isBlank() && !bucket.isBlank();
        this.publicUploadConfigured = configured && !publicBaseUrl.isBlank();

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

    /** Uploads opaque bytes under "attachments/" and returns the storage
     *  key (not a URL) -- for private content fetched via the
     *  authenticated download proxy, e.g. encrypted chat attachments. */
    public String upload(byte[] data) {
        return upload(data, "attachments/", null, "");
    }

    /** Uploads bytes under "properties/" intended for public, direct
     *  access and returns the full public URL. Requires
     *  R2_PUBLIC_BASE_URL -- without it, callers get a clear error
     *  rather than a URL that won't actually load anything. Defaults to
     *  image/jpeg for the existing photo-upload call site; use the
     *  contentType/extension overload for anything else (e.g. video). */
    public String uploadPublic(byte[] data) {
        return uploadPublic(data, "image/jpeg", ".jpg");
    }

    /**
     * Same as {@link #uploadPublic(byte[])} but with an explicit
     * content type and file extension -- video needs both: a correct
     * Content-Type header for players that check it, and a real
     * extension in the URL for players/CDNs that infer format from the
     * path rather than sniffing bytes.
     */
    public String uploadPublic(byte[] data, String contentType, String extension) {
        requirePublicConfigured();
        String key = upload(data, "properties/", contentType, extension);
        return publicBaseUrl + "/" + key;
    }

    private String upload(byte[] data, String keyPrefix, String contentType, String extension) {
        requireConfigured();
        String key = keyPrefix + UUID.randomUUID() + (extension == null ? "" : extension);
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder().bucket(bucket).key(key);
        if (contentType != null && !contentType.isBlank()) {
            requestBuilder = requestBuilder.contentType(contentType);
        }
        s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(data));
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

    private void requirePublicConfigured() {
        requireConfigured();
        if (!publicUploadConfigured) {
            throw new IllegalStateException(
                    "R2 public uploads are not configured. Enable public access on the R2 " +
                    "bucket and set the R2_PUBLIC_BASE_URL environment variable to the " +
                    "resulting r2.dev (or custom domain) URL."
            );
        }
    }
}
