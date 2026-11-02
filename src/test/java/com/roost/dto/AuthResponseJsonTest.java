package com.roost.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthResponseJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode serialize(AuthResponse response) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(response));
    }

    @Test
    @DisplayName("a new account serializes the flag as isNewUser, not newUser")
    void newUserFlagUsesTheDocumentedKey() throws Exception {
        JsonNode json = serialize(new AuthResponse("tok", true));

        assertTrue(json.get("isNewUser").asBoolean());
        assertFalse(json.has("newUser"));
    }

    @Test
    @DisplayName("an existing account serializes isNewUser as false")
    void existingAccountIsNotNew() throws Exception {
        JsonNode json = serialize(new AuthResponse("tok", false));

        assertFalse(json.get("isNewUser").asBoolean());
        assertFalse(json.has("newUser"));
    }

    @Test
    @DisplayName("the single-argument constructor defaults isNewUser to false")
    void defaultsToNotNew() throws Exception {
        JsonNode json = serialize(new AuthResponse("tok"));

        assertEquals("tok", json.get("token").asText());
        assertFalse(json.get("isNewUser").asBoolean());
    }
}
