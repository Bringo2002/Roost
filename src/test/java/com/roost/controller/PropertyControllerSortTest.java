package com.roost.controller;

import com.roost.exception.GlobalExceptionHandler;
import com.roost.service.PropertyRiskService;
import com.roost.service.PropertyService;
import com.roost.service.RateLimiterService;
import com.roost.service.RentEstimateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code sort} parameter on GET /api/properties/filter: "recommended"
 * switches to the score-ordered search; anything else is rejected rather than
 * silently ignored; leaving it out keeps the existing behaviour.
 */
class PropertyControllerSortTest {

    private PropertyService propertyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        propertyService = mock(PropertyService.class);
        PropertyController controller = new PropertyController(
                propertyService, mock(PropertyRiskService.class),
                mock(RentEstimateService.class), mock(RateLimiterService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                // Same pinning as PropertyControllerPrivacyTest: standalone MockMvc would
                // otherwise pick an XML converter that the S3 SDK pulls onto the classpath.
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
        when(propertyService.filterRecommended(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(propertyService.filter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private void verifyRecommendedUsed() {
        verify(propertyService).filterRecommended(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
        verify(propertyService, never()).filter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("sort=recommended uses the score-ordered search with the requested page, unsorted")
    void recommendedUsesScoreOrderedSearch() throws Exception {
        mockMvc.perform(get("/api/properties/filter")
                        .param("sort", "recommended").param("page", "2").param("size", "10").param("q", "sunny"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(propertyService).filterRecommended(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        // The ORDER BY lives in the query; a Sort here would be appended to it.
        assertFalse(pageable.getValue().getSort().isSorted());
    }

    @Test
    @DisplayName("sort is case-insensitive and wins over lat/lng")
    void recommendedIsCaseInsensitiveAndIgnoresLocation() throws Exception {
        mockMvc.perform(get("/api/properties/filter")
                        .param("sort", " Recommended ").param("size", "10")
                        .param("lat", "-1.28").param("lng", "36.82"))
                .andExpect(status().isOk());

        verifyRecommendedUsed();
    }

    @Test
    @DisplayName("no sort keeps the existing search")
    void noSortKeepsExistingBehaviour() throws Exception {
        mockMvc.perform(get("/api/properties/filter").param("size", "10"))
                .andExpect(status().isOk());

        verify(propertyService).filter(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
        verify(propertyService, never()).filterRecommended(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("a blank sort counts as absent")
    void blankSortCountsAsAbsent() throws Exception {
        mockMvc.perform(get("/api/properties/filter").param("sort", "  ").param("size", "10"))
                .andExpect(status().isOk());

        verify(propertyService, never()).filterRecommended(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("an unknown sort is a 400, not silently ignored")
    void unknownSortIs400() throws Exception {
        mockMvc.perform(get("/api/properties/filter").param("sort", "cheapest").param("size", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(propertyService);
    }

    @Test
    @DisplayName("sort without size is a 400, because the unpaginated search cannot honour it")
    void sortWithoutSizeIs400() throws Exception {
        mockMvc.perform(get("/api/properties/filter").param("sort", "recommended"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(propertyService);
    }
}
