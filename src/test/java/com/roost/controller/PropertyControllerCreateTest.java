package com.roost.controller;

import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.PropertyRiskService;
import com.roost.service.PropertyService;
import com.roost.service.RateLimiterService;
import com.roost.service.RentEstimateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/properties binds the request body straight onto the Property
 * entity, so the controller itself has to make sure a caller cannot choose
 * which row gets written.
 */
class PropertyControllerCreateTest {

    private PropertyService propertyService;
    private MockMvc mockMvc;
    private User landlord;

    @BeforeEach
    void setUp() {
        propertyService = mock(PropertyService.class);

        PropertyController controller = new PropertyController(
                propertyService, mock(PropertyRiskService.class),
                mock(RentEstimateService.class), mock(RateLimiterService.class));

        // Same standalone setup as PropertyControllerPrivacyTest; see the
        // comment there for why the converter is pinned to Jackson JSON.
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()))
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

    @Test
    @DisplayName("POST /api/properties ignores a client-supplied id, so it can never overwrite another listing")
    void creationIgnoresClientSuppliedId() throws Exception {
        Property created = new Property();
        created.setId(200L);
        created.setTitle("Sunny Bedsitter");
        created.setStatus("DRAFT");
        created.setOwner(landlord);
        when(propertyService.addProperty(any(Property.class))).thenReturn(created);

        // 100 stands for a listing that belongs to someone else.
        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 100, \"title\": \"Sunny Bedsitter\", \"status\": \"DRAFT\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Property> sent = ArgumentCaptor.forClass(Property.class);
        verify(propertyService).addProperty(sent.capture());
        assertNull(sent.getValue().getId());
        assertSame(landlord, sent.getValue().getOwner());
    }
}
