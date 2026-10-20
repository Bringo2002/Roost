package com.roost.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * Bean Validation failures on a {@code @Valid @RequestBody} are the
     * client's mistake, not a server fault. Without this they fall through
     * to the catch-all {@code Exception} handler below and surface as a
     * 500. Field messages are sorted so the response is deterministic.
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", msg.isBlank() ? "Invalid request" : msg);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * A path or query parameter that cannot be converted to its declared type
     * (e.g. {@code /api/properties/abc} for a {@code Long id}) is the client's
     * mistake. Spring's own message names the Java types involved ("Failed to
     * convert value of type 'java.lang.String' to required type ..."), which
     * is an internal detail, so respond with a fixed message that only names
     * the parameter. The parameter name comes from the method signature, not
     * from user input, and the offending value is deliberately not echoed.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter '{}' (required type {})", ex.getName(), ex.getRequiredType());
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Invalid value for parameter '" + ex.getName() + "'");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        Map<String, String> errorResponse = new HashMap<>();
        // Jackson's cause text names internal classes, field paths and enum
        // constants, so it stays in the log and the client gets a fixed message.
        errorResponse.put("error", "Malformed or invalid request body");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        log.warn("Unhandled RuntimeException on {}", ex.getClass().getSimpleName(), ex);
        // Anything reaching here was not thrown deliberately for a client (that
        // is what ApiException is for). Framework and database messages can
        // carry SQL, constraint names, class names and user data, so they stay
        // in the log above and the client gets a fixed message.
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Invalid request");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);
        // The message of an unexpected server fault is never echoed; it is in the log above.
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "An unexpected error occurred. Please try again.");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
