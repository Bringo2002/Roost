package com.roost.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerUploadTest {

    @Test
    @DisplayName("an upload over the server limit is a 413 with a clear message, not 'Invalid request'")
    void oversizedUploadIs413() {
        ResponseEntity<Map<String, String>> response =
                new GlobalExceptionHandler().handleUploadTooLarge(new MaxUploadSizeExceededException(6 * 1024 * 1024));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("The file is too large. The limit is 5 MB.", response.getBody().get("error"));
    }
}
