package com.roost.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Requests that match no route are the client's mistake. Spring raises
 * {@link NoResourceFoundException} (unknown path) and
 * {@link HttpRequestMethodNotSupportedException} (known path, wrong verb);
 * without dedicated handlers both fall through to the catch-all and come back
 * as a 500, which reads as a server fault in monitoring and to scanners.
 *
 * <p>Called directly because MockMvc's standalone setup answers an unmapped
 * path itself and never reaches the advice, so it cannot reproduce this.
 */
class GlobalExceptionHandlerRoutingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("an unknown path is a 404 with a fixed message, not a 500")
    void unknownPathIs404() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNoResourceFound(new NoResourceFoundException(HttpMethod.GET, "api/nope", "No static resource api/nope."));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not found", response.getBody().get("error"));
    }

    @Test
    @DisplayName("the 404 body does not echo the requested path")
    void notFoundDoesNotEchoThePath() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNoResourceFound(new NoResourceFoundException(HttpMethod.GET, "api/secret-probe", "No static resource api/secret-probe."));

        assertFalse(response.getBody().get("error").contains("secret-probe"));
    }

    @Test
    @DisplayName("the wrong HTTP method on a known path is a 405, not a 500")
    void wrongMethodIs405() {
        ResponseEntity<Map<String, String>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST")));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("Method not allowed", response.getBody().get("error"));
    }
}
