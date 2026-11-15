package com.roost.service;

import com.roost.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
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
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores verification documents (national IDs, title deeds) in a private
 * bucket that has no public access. Files are written and read only by this
 * server, through its own credentials; no public or presigned URL is ever
 * produced, and callers hold only the opaque key.
 *
 * <p>Uses its own bucket and its own IAM keys, separate from
 * {@link R2StorageService}, so the keys that sign public media uploads can
 * never reach identity documents. When any setting is blank the service is
 * simply not configured and every operation fails with 503.
 */
@Service
public class PrivateDocumentStorage {

    /** Every key looks like this; anything else is refused before reaching S3. */
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^verification/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|pdf)$");

    private final S3Client s3Client;
    private final String bucket;

    @Autowired
    public PrivateDocumentStorage(
            @Value("${private-docs.bucket:}") String bucket,
            @Value("${private-docs.access-key-id:}") String accessKeyId,
            @Value("${private-docs.secret-access-key:}") String secretAccessKey,
            @Value("${private-docs.region:eu-north-1}") String region) {
        boolean hasKeyId = !accessKeyId.isBlank();
        boolean hasSecret = !secretAccessKey.isBlank();
        // A half-set pair is a deployment mistake; fail at startup, not on first upload.
        if (hasKeyId != hasSecret) {
            throw new IllegalArgumentException(
                    "S3_PRIVATE_ACCESS_KEY_ID and S3_PRIVATE_SECRET_ACCESS_KEY must both be set or both be blank.");
        }
        this.bucket = bucket;
        // Explicit keys only: never fall back to ambient or instance credentials,
        // which could silently widen access to this bucket.
        this.s3Client = (!bucket.isBlank() && hasKeyId)
                ? S3Client.builder()
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                        .region(Region.of(region))
                        .build()
                : null;
    }

    /** For tests: use the given client and bucket. */
    PrivateDocumentStorage(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    /** @return true when the bucket and credentials are all set */
    public boolean isConfigured() {
        return s3Client != null;
    }

    /**
     * Stores a file that has already passed {@link VerificationFilePolicy}.
     *
     * @param data the file bytes
     * @param accepted the detected type from the policy
     * @return the opaque key to keep in the database
     * @throws ApiException 503 if private storage is not configured
     */
    public String store(byte[] data, VerificationFilePolicy.Accepted accepted) {
        requireConfigured();
        String key = "verification/" + UUID.randomUUID() + "." + accepted.extension();
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(accepted.contentType())
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromBytes(data));
        return key;
    }

    /**
     * Reads a stored file.
     *
     * @param key a key returned by {@link #store}
     * @return the file bytes
     * @throws IllegalArgumentException if the key is not one this service produced
     * @throws ApiException 503 if private storage is not configured
     */
    public byte[] load(String key) {
        requireConfigured();
        requireValidKey(key);
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    /**
     * Deletes a stored file.
     *
     * @param key a key returned by {@link #store}
     * @throws IllegalArgumentException if the key is not one this service produced
     * @throws ApiException 503 if private storage is not configured
     */
    public void delete(String key) {
        requireConfigured();
        requireValidKey(key);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private void requireConfigured() {
        if (s3Client == null) {
            throw ApiException.serviceUnavailable("Document storage is not available right now.");
        }
    }

    private static void requireValidKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Not a verification document key.");
        }
    }
}
