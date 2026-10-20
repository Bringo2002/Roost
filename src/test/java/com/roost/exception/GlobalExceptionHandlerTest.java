package com.roost.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
