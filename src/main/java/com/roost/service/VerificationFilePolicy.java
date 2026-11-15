package com.roost.service;

import com.roost.exception.ApiException;

/**
 * Decides whether an uploaded verification document is acceptable.
 *
 * <p>The type is taken from the file's own first bytes, never from the
 * content type or file name the client claims, so a page of script renamed
 * to {@code id.jpg} is refused. Only JPEG, PNG and PDF are accepted, which
 * covers phone photos of an ID and scanned or exported title deeds.
 */
public final class VerificationFilePolicy {

    /** Largest document accepted, in bytes. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** What an accepted file is, as detected from its content. */
    public record Accepted(String contentType, String extension) {}

    private VerificationFilePolicy() {
    }

    /**
     * Checks a file's size and type.
     *
     * @param data the uploaded bytes
     * @return the detected content type and file extension
     * @throws ApiException 400 if the file is empty, too large or not a JPEG, PNG or PDF
     */
    public static Accepted check(byte[] data) {
        if (data == null || data.length == 0) {
            throw ApiException.badRequest("The file is empty. Please choose a photo or PDF.");
        }
        if (data.length > MAX_BYTES) {
            throw ApiException.badRequest("The file is too large. The limit is 5 MB.");
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return new Accepted("image/jpeg", "jpg");
        }
        if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new Accepted("image/png", "png");
        }
        if (startsWith(data, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            return new Accepted("application/pdf", "pdf");
        }
        throw ApiException.badRequest("Unsupported file type. Please upload a JPEG, PNG or PDF.");
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
