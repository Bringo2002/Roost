package com.roost.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down which endorsement/verifier fields the public property
 * payload may carry. The endorsement token is a bearer credential: anyone
 * holding it can POST /api/properties/endorse/{token} (unauthenticated)
 * and stamp "landlord endorsed" onto a listing, which is the trust badge
 * property cards display. It must never ride along in payloads that any
 * anonymous visitor can fetch.
 */
class PropertyResponseDtoTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private User owner;
    private User stranger;
    private Property property;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setName("Caretaker Carl");
        owner.setRole(Role.LANDLORD);

        stranger = new User();
        stranger.setId(2L);
        stranger.setRole(Role.TENANT);

        property = new Property();
        property.setId(100L);
        property.setTitle("Sunny Bedsitter");
        property.setOwner(owner);
        property.setManagerRole("CARETAKER");
        property.setLandlordEndorsed(true);
        property.setEndorsementToken(TOKEN);
        property.setOwnerVerifyName("Mama Wanjiku");
        property.setOwnerVerifyPhone("+254712345678");
        property.setRiskFlags(List.of("unverified_photos"));
    }

    @Test
    @DisplayName("default (public) view redacts the endorsement token and verifier contact details")
    void publicViewRedactsPrivateFields() {
        PropertyResponseDto dto = PropertyResponseDto.from(property);

        assertNull(dto.getEndorsementToken());
        assertNull(dto.getOwnerVerifyName());
        assertNull(dto.getOwnerVerifyPhone());
    }

    @Test
    @DisplayName("public view keeps every field the property card renders, including the endorsed badge signal")
    void publicViewKeepsCardFields() {
        PropertyResponseDto dto = PropertyResponseDto.from(property);

        assertEquals("Sunny Bedsitter", dto.getTitle());
        assertEquals("CARETAKER", dto.getManagerRole());
        assertTrue(dto.isLandlordEndorsed());
        assertEquals(List.of("unverified_photos"), dto.getRiskFlags());
        assertEquals(1L, dto.getOwner().getId());
    }

    @Test
    @DisplayName("public view serializes the redacted fields as JSON null, so the response shape is unchanged")
    void publicViewJsonShapeIsUnchanged() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(PropertyResponseDto.from(property)));

        assertTrue(json.has("endorsementToken"));
        assertTrue(json.get("endorsementToken").isNull());
        assertTrue(json.get("ownerVerifyName").isNull());
        assertTrue(json.get("ownerVerifyPhone").isNull());
        assertTrue(json.get("landlordEndorsed").asBoolean());
    }

    @Test
    @DisplayName("the token never appears anywhere in a public list payload")
    void publicListNeverContainsToken() throws Exception {
        String body = mapper.writeValueAsString(PropertyResponseDto.from(List.of(property)));

        assertFalse(body.contains(TOKEN));
        assertFalse(body.contains("+254712345678"));
    }

    @Test
    @DisplayName("owner view includes the token and verifier details")
    void ownerViewIncludesPrivateFields() {
        PropertyResponseDto dto = PropertyResponseDto.forOwner(property);

        assertEquals(TOKEN, dto.getEndorsementToken());
        assertEquals("Mama Wanjiku", dto.getOwnerVerifyName());
        assertEquals("+254712345678", dto.getOwnerVerifyPhone());
    }

    @Test
    @DisplayName("forViewer includes private fields only when the viewer owns the listing")
    void forViewerIsOwnerAware() {
        assertEquals(TOKEN, PropertyResponseDto.forViewer(property, null, owner).getEndorsementToken());
        assertNull(PropertyResponseDto.forViewer(property, null, stranger).getEndorsementToken());
        assertNull(PropertyResponseDto.forViewer(property, null, null).getEndorsementToken());
    }

    @Test
    @DisplayName("forViewer treats a listing with no owner as not owned by anyone")
    void forViewerHandlesMissingOwner() {
        property.setOwner(null);

        assertNull(PropertyResponseDto.forViewer(property, null, owner).getEndorsementToken());
    }
}
