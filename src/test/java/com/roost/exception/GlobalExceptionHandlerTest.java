package com.roost.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@link GlobalExceptionHandler}, driven through a
 * tiny stub controller so each exception path can be triggered by a real
 * request. Same standaloneSetup pattern as the controller tests.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @RestController
    @RequestMapping("/test")
    static class StubController {

        @GetMapping("/items/{id}")
        Map<String, Long> byId(@PathVariable("id") Long id) {
            return Map.of("id", id);
        }

        @GetMapping("/search")
        Map<String, Integer> search(@RequestParam("page") Integer page) {
            return Map.of("page", page);
        }

        @PostMapping("/items")
        Map<String, String> create(@RequestBody Payload payload) {
            return Map.of("status", "created");
        }

        @GetMapping("/boom/db")
        String dbError() {
            throw new DataIntegrityViolationException(
                    "could not execute statement [ERROR: duplicate key value violates unique "
                            + "constraint \"uk_users_email\" Detail: Key (email)=(victim@example.com) "
                            + "already exists.]");
        }

        @GetMapping("/boom/npe")
        String npe() {
            throw new NullPointerException(
                    "Cannot invoke \"Object.toString()\" because \"internalField\" is null");
        }

        @GetMapping("/boom/cause")
        String causeOnly() {
            throw new RuntimeException(new IllegalStateException("secret-cause-detail"));
        }

        @GetMapping("/boom/checked")
        String checked() throws Exception {
            throw new Exception("jdbc:postgresql://db.internal:5432/roost?password=hunter2");
        }

        @GetMapping("/boom/api")
        String api() {
            throw ApiException.notFound("Property not found");
        }
    }

    static class Payload {
        public Integer count;
    }

    @Test
    @DisplayName("non-numeric path variable returns 400 with a generic message")
    void pathVariableTypeMismatch_returnsGenericBadRequest() throws Exception {
        mockMvc.perform(get("/test/items/{id}", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for parameter 'id'"));
    }

    @Test
    @DisplayName("non-numeric query parameter returns 400 with a generic message")
    void queryParamTypeMismatch_returnsGenericBadRequest() throws Exception {
        mockMvc.perform(get("/test/search").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for parameter 'page'"));
    }

    @Test
    @DisplayName("type mismatch response leaks neither Java type names nor the submitted value")
    void typeMismatch_doesNotLeakInternalsOrEchoInput() throws Exception {
        String body = mockMvc.perform(get("/test/items/{id}", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("java.lang"), "body must not name Java types: " + body);
        assertFalse(body.contains("Failed to convert"), "body must not echo Spring's message: " + body);
        assertFalse(body.contains("not-a-number"), "body must not echo the submitted value: " + body);
    }

    @Test
    @DisplayName("a well-formed value is unaffected by the handler")
    void validValue_returnsOk() throws Exception {
        mockMvc.perform(get("/test/items/{id}", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    @DisplayName("malformed JSON body returns 400 with a generic message")
    void malformedJson_returnsGenericBadRequest() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or invalid request body"));
    }

    @Test
    @DisplayName("missing request body returns 400 with a generic message")
    void missingBody_returnsGenericBadRequest() throws Exception {
        mockMvc.perform(post("/test/items").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or invalid request body"));
    }

    @Test
    @DisplayName("wrong field type response is exactly the generic body, with no Jackson internals")
    void wrongFieldType_doesNotLeakJacksonCause() throws Exception {
        String body = mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // Exact match: any class name, field path or submitted value would break it.
        assertEquals("{\"error\":\"Malformed or invalid request body\"}", body);
    }

    @Test
    @DisplayName("a well-formed body is unaffected by the handler")
    void validBody_returnsOk() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("created"));
    }

    @Test
    @DisplayName("a database error returns a fixed 400 body, with no SQL, constraint or user data")
    void databaseError_doesNotLeakSqlOrData() throws Exception {
        String body = mockMvc.perform(get("/test/boom/db"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals("{\"error\":\"Invalid request\"}", body);
    }

    @Test
    @DisplayName("a NullPointerException returns a fixed 400 body, with no variable names")
    void nullPointer_doesNotLeakInternalNames() throws Exception {
        String body = mockMvc.perform(get("/test/boom/npe"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals("{\"error\":\"Invalid request\"}", body);
    }

    @Test
    @DisplayName("a wrapped cause is not surfaced, including its class name")
    void wrappedCause_doesNotLeak() throws Exception {
        String body = mockMvc.perform(get("/test/boom/cause"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals("{\"error\":\"Invalid request\"}", body);
    }

    @Test
    @DisplayName("an unexpected checked exception returns a fixed 500 body")
    void unexpectedException_returnsFixedServerError() throws Exception {
        String body = mockMvc.perform(get("/test/boom/checked"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertEquals("{\"error\":\"An unexpected error occurred. Please try again.\"}", body);
    }

    @Test
    @DisplayName("deliberate ApiException messages still reach the client unchanged")
    void apiException_messageIsStillReturned() throws Exception {
        mockMvc.perform(get("/test/boom/api"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Property not found"));
    }
}
