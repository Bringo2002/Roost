package com.roost.service;

import com.roost.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerificationFilePolicyTest {

    private static byte[] withHeader(int size, int... header) {
        byte[] data = new byte[size];
        for (int i = 0; i < header.length; i++) {
            data[i] = (byte) header[i];
        }
        return data;
    }

    private static void assertRejected(byte[] data) {
        ApiException ex = assertThrows(ApiException.class, () -> VerificationFilePolicy.check(data));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    @DisplayName("a JPEG is accepted and detected from its content")
    void acceptsJpeg() {
        VerificationFilePolicy.Accepted accepted = VerificationFilePolicy.check(withHeader(100, 0xFF, 0xD8, 0xFF, 0xE0));
        assertEquals("image/jpeg", accepted.contentType());
        assertEquals("jpg", accepted.extension());
    }

    @Test
    @DisplayName("a PNG is accepted")
    void acceptsPng() {
        VerificationFilePolicy.Accepted accepted =
                VerificationFilePolicy.check(withHeader(100, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A));
        assertEquals("image/png", accepted.contentType());
        assertEquals("png", accepted.extension());
    }

    @Test
    @DisplayName("a PDF is accepted")
    void acceptsPdf() {
        VerificationFilePolicy.Accepted accepted = VerificationFilePolicy.check("%PDF-1.7 body".getBytes(StandardCharsets.US_ASCII));
        assertEquals("application/pdf", accepted.contentType());
        assertEquals("pdf", accepted.extension());
    }

    @Test
    @DisplayName("a file of exactly the size limit is accepted")
    void acceptsFileAtTheLimit() {
        VerificationFilePolicy.check(withHeader(VerificationFilePolicy.MAX_BYTES, 0xFF, 0xD8, 0xFF));
    }

    @Test
    @DisplayName("a file one byte over the limit is rejected")
    void rejectsFileOverTheLimit() {
        assertRejected(withHeader(VerificationFilePolicy.MAX_BYTES + 1, 0xFF, 0xD8, 0xFF));
    }

    @Test
    @DisplayName("empty and missing files are rejected")
    void rejectsEmpty() {
        assertRejected(new byte[0]);
        assertRejected(null);
    }

    @Test
    @DisplayName("script or markup renamed to look like an image is rejected")
    void rejectsDisguisedContent() {
        assertRejected("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        assertRejected("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a file shorter than any signature is rejected")
    void rejectsTruncatedHeader() {
        assertRejected(Arrays.copyOf(new byte[] {(byte) 0xFF, (byte) 0xD8}, 2));
    }
}
