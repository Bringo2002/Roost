package com.roost.controller;

import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.MediaHash;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.MediaHashRepository;
import com.roost.service.PropertyRiskService;
import com.roost.service.PropertyService;
import com.roost.service.R2StorageService;
import com.roost.service.RateLimiterService;
import com.roost.service.RentEstimateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for PropertyController endpoints:
 * <ul>
 *   <li>confirm-upload R2 URL prefix validation (M4)</li>
 *   <li>addProperty trust-badge sanitization completeness (H2 supplement)</li>
 * </ul>
 */
class PropertyControllerSecurityTest {

    private PropertyService propertyService;
    private R2StorageService r2StorageService;
    private MediaHashRepository mediaHashRepository;
    private MockMvc mockMvc;
    private User landlord;

    @BeforeEach
    void setUp() {
        propertyService = mock(PropertyService.class);
        r2StorageService = mock(R2StorageService.class);
        mediaHashRepository = mock(MediaHashRepository.class);

        PropertyController controller = new PropertyController(
                propertyService, mock(PropertyRiskService.class),
                mock(RentEstimateService.class), mock(RateLimiterService.class));

        // Field-injected dependencies — set via reflection.
        ReflectionTestUtils.setField(controller, "r2StorageService", r2StorageService);
        ReflectionTestUtils.setField(controller, "mediaHashRepository", mediaHashRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        landlord = new User();
        landlord.setId(1L);
        landlord.setName("Landlord Lee");
        landlord.setRole(Role.LANDLORD);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(landlord, null, landlord.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── M4: confirm-upload R2 URL prefix ────────────────────────────────────

    @Test
    @DisplayName("confirm-upload rejects a publicUrl that does not match the R2 base URL")
    void confirmUpload_rejectsExternalUrl() throws Exception {
        when(r2StorageService.getPublicBaseUrl()).thenReturn("https://media.roost.co.ke");
        when(mediaHashRepository.findByContentHash(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/properties/confirm-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHash\":\"abc123\","
                                + "\"publicUrl\":\"https://evil.example.com/malware.jpg\","
                                + "\"key\":\"photos/123.jpg\","
                                + "\"contentType\":\"image/jpeg\","
                                + "\"sizeBytes\":\"1024\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("publicUrl must point to the configured R2 bucket."));

        verify(mediaHashRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm-upload accepts a publicUrl matching the R2 base URL")
    void confirmUpload_acceptsValidUrl() throws Exception {
        when(r2StorageService.getPublicBaseUrl()).thenReturn("https://media.roost.co.ke");
        when(mediaHashRepository.findByContentHash(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/properties/confirm-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHash\":\"abc123\","
                                + "\"publicUrl\":\"https://media.roost.co.ke/photos/123.jpg\","
                                + "\"key\":\"photos/123.jpg\","
                                + "\"contentType\":\"image/jpeg\","
                                + "\"sizeBytes\":\"1024\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true));

        verify(mediaHashRepository).save(any(MediaHash.class));
    }

    // ── H2 supplement: addProperty sanitizes reportCount ─────────────────────

    @Test
    @DisplayName("POST /api/properties sanitizes reportCount to prevent inflation")
    void addProperty_sanitizesReportCount() throws Exception {
        Property created = new Property();
        created.setId(200L);
        created.setTitle("Clean Bedsitter");
        created.setStatus("DRAFT");
        created.setOwner(landlord);
        created.setReportCount(0L);
        when(propertyService.addProperty(any(Property.class))).thenReturn(created);

        // Client sends reportCount: 999 to try to inflate the count.
        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Clean Bedsitter\","
                                + "\"status\":\"DRAFT\","
                                + "\"reportCount\":999}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Property> sent = ArgumentCaptor.forClass(Property.class);
        verify(propertyService).addProperty(sent.capture());
        // reportCount must be null or 0 — never the client-supplied 999.
        assertTrue(sent.getValue().getReportCount() == null
                        || sent.getValue().getReportCount() == 0L,
                "reportCount should be sanitized: " + sent.getValue().getReportCount());
    }

    @Test
    @DisplayName("POST /api/properties sanitizes all trust badge fields")
    void addProperty_sanitizesTrustBadges() throws Exception {
        Property created = new Property();
        created.setId(200L);
        created.setTitle("Trust Test");
        created.setStatus("DRAFT");
        created.setOwner(landlord);
        when(propertyService.addProperty(any(Property.class))).thenReturn(created);

        // Client sends all trust badges as true to try to self-verify.
        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Trust Test\","
                                + "\"status\":\"DRAFT\","
                                + "\"gpsVerified\":true,"
                                + "\"photoApproved\":true,"
                                + "\"documentVerified\":true,"
                                + "\"landlordEndorsed\":true,"
                                + "\"verified\":true,"
                                + "\"viewCount\":9999,"
                                + "\"saveCount\":9999}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Property> sent = ArgumentCaptor.forClass(Property.class);
        verify(propertyService).addProperty(sent.capture());
        Property p = sent.getValue();
        assertFalse(p.isGpsVerified(), "gpsVerified should be sanitized");
        assertFalse(p.isPhotoApproved(), "photoApproved should be sanitized");
        assertNull(p.getDocumentVerified(), "documentVerified should be sanitized to null");
        assertFalse(p.isLandlordEndorsed(), "landlordEndorsed should be sanitized");
        assertFalse(p.isVerified(), "verified should be sanitized");
        assertEquals(0, p.getViewCount(), "viewCount should be sanitized");
        assertEquals(0, p.getSaveCount(), "saveCount should be sanitized");
    }
}
