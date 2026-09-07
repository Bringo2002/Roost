package com.roost.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException ex) {
        // ApiException is thrown deliberately with a client-safe message
        // (e.g. "Phone not verified") -- fine to return as-is.
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        Map<String, String> errorResponse = new HashMap<>();
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "Invalid JSON payload format";
        errorResponse.put("error", msg);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        log.warn("Unhandled RuntimeException on {}", ex.getClass().getSimpleName(), ex);
        Map<String, String> errorResponse = new HashMap<>();
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            Throwable cause = ex.getCause();
            if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
                msg = cause.getMessage();
            }
        }
        errorResponse.put("error", (msg != null && !msg.isBlank()) ? msg : "Invalid request");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, String> errorResponse = new HashMap<>();
        String msg = ex.getMessage();
        errorResponse.put("error", (msg != null && !msg.isBlank()) ? msg : "An unexpected error occurred. Please try again.");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
