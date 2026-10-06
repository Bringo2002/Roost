package com.roost.controller;

import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Property;
import com.roost.service.PropertyRiskService;
import com.roost.service.PropertyService;
import com.roost.service.RateLimiterService;
import com.roost.service.RentEstimateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How the list endpoints turn the page/size query parameters into a
 * Pageable: the default page size, the cap, and clamping of nonsense values.
 */
class PropertyPagingParamsTest {

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
                // Pin Jackson JSON: standaloneSetup() would otherwise pick up
                // the XML converter this project's AWS S3 SDK pulls in.
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()))
                .build();
    }

    // ─── GET /api/properties/by-owner/{ownerId}: paginated by default ───

    private Pageable byOwnerPageable(String... queryParams) throws Exception {
        when(propertyService.getPublishedPropertiesByOwner(any(), any(Pageable.class))).thenReturn(List.<Property>of());
        var request = get("/api/properties/by-owner/7");
        for (int i = 0; i < queryParams.length; i += 2) {
            request = request.param(queryParams[i], queryParams[i + 1]);
        }
        mockMvc.perform(request).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(propertyService).getPublishedPropertiesByOwner(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("by-owner: defaults to the first page of 20")
    void byOwnerDefaultsToFirstPageOfTwenty() throws Exception {
        Pageable pageable = byOwnerPageable();

        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
    }

    @Test
    @DisplayName("by-owner: caps the page size at 50")
    void byOwnerCapsTheSizeAtFifty() throws Exception {
        assertEquals(50, byOwnerPageable("size", "500").getPageSize());
    }

    @Test
    @DisplayName("by-owner: raises a non-positive size to 1 and a negative page to 0")
    void byOwnerClampsNonsenseValues() throws Exception {
        Pageable pageable = byOwnerPageable("size", "-5", "page", "-3");

        assertEquals(1, pageable.getPageSize());
        assertEquals(0, pageable.getPageNumber());
    }

    @Test
    @DisplayName("by-owner: honours an explicit page and size")
    void byOwnerHonoursPageAndSize() throws Exception {
        Pageable pageable = byOwnerPageable("page", "2", "size", "5");

        assertEquals(2, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
    }

    // ─── GET /api/properties: pagination is opt-in ───

    @Test
    @DisplayName("root feed: with a size it caps at 50 and defaults to page 0")
    void rootFeedCapsTheSize() throws Exception {
        when(propertyService.getAllProperties(any(), any(), any(Pageable.class))).thenReturn(List.<Property>of());

        mockMvc.perform(get("/api/properties").param("size", "500")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(propertyService).getAllProperties(any(), any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
    }

    // ─── GET /api/properties/filter: pagination is opt-in ───

    @Test
    @DisplayName("filter: with a size it clamps the size to at least 1 and the page to at least 0")
    void filterClampsNonsenseValues() throws Exception {
        when(propertyService.filter(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(Pageable.class))).thenReturn(List.<Property>of());

        mockMvc.perform(get("/api/properties/filter").param("size", "0").param("page", "-1"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(propertyService).filter(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(1, captor.getValue().getPageSize());
    }
}
