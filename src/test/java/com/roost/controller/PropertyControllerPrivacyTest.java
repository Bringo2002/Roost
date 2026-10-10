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
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint-level guarantee that the endorsement token and verifier contact
 * details reach only the people entitled to them: the listing's owner and
 * whoever holds the token. Public browsing endpoints (the ones feeding
 * property cards) must never carry them.
 */
class PropertyControllerPrivacyTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private PropertyService propertyService;
    private PropertyRiskService propertyRiskService;
    private MockMvc mockMvc;

    private User owner;
    private User stranger;
    private Property property;

    @BeforeEach
    void setUp() {
        propertyService = mock(PropertyService.class);
        propertyRiskService = mock(PropertyRiskService.class);

        PropertyController controller = new PropertyController(
                propertyService, propertyRiskService,
                mock(RentEstimateService.class), mock(RateLimiterService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                // standaloneSetup() doesn't get Spring Boot's real content-
                // negotiation config -- it independently discovers whatever
                // HttpMessageConverters are on the classpath, and picks an
                // XML converter ahead of JSON here (this project's AWS S3
                // SDK dependency pulls one in transitively). Pin the
                // converter list to Jackson JSON explicitly so responses in
                // this test class always serialize the way the real app
                // (and this test's jsonPath assertions) expect.
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

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
        property.setStatus("PUBLISHED");
        property.setOwner(owner);
        property.setManagerRole("CARETAKER");
        property.setLandlordEndorsed(true);
        property.setEndorsementToken(TOKEN);
        property.setOwnerVerifyName("Mama Wanjiku");
        property.setOwnerVerifyPhone("+254712345678");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    @DisplayName("GET /api/properties (public feed) never exposes the token or verifier details")
    void publicFeedRedactsPrivateFields() throws Exception {
        when(propertyService.getAllProperties()).thenReturn(List.of(property));

        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endorsementToken").value((Object) null))
                .andExpect(jsonPath("$[0].ownerVerifyName").value((Object) null))
                .andExpect(jsonPath("$[0].ownerVerifyPhone").value((Object) null))
                // The trust badge the card shows must still be there.
                .andExpect(jsonPath("$[0].landlordEndorsed").value(true))
                .andExpect(jsonPath("$[0].managerRole").value("CARETAKER"));
    }

    @Test
    @DisplayName("GET /api/properties/nearby never exposes the token")
    void nearbyRedactsPrivateFields() throws Exception {
        when(propertyService.getNearby(anyDouble(), anyDouble(), anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(property));

        mockMvc.perform(get("/api/properties/nearby").param("lat", "-1.29").param("lng", "36.82"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endorsementToken").value((Object) null));
    }

    @Test
    @DisplayName("GET /api/properties/{id} as an anonymous visitor redacts the token")
    void detailAsAnonymousRedacts() throws Exception {
        when(propertyService.getPropertyDetail(100L)).thenReturn(property);

        mockMvc.perform(get("/api/properties/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endorsementToken").value((Object) null))
                .andExpect(jsonPath("$.ownerVerifyPhone").value((Object) null));
    }

    @Test
    @DisplayName("GET /api/properties/{id} as a different signed-in user redacts the token")
    void detailAsStrangerRedacts() throws Exception {
        when(propertyService.getPropertyDetail(100L)).thenReturn(property);
        authenticateAs(stranger);

        mockMvc.perform(get("/api/properties/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endorsementToken").value((Object) null));
    }

    @Test
    @DisplayName("GET /api/properties/{id} as the owner includes the token")
    void detailAsOwnerIncludesToken() throws Exception {
        when(propertyService.getPropertyDetail(100L)).thenReturn(property);
        authenticateAs(owner);

        mockMvc.perform(get("/api/properties/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endorsementToken").value(TOKEN))
                .andExpect(jsonPath("$.ownerVerifyName").value("Mama Wanjiku"));
    }

    @Test
    @DisplayName("GET /api/properties/my-listings includes the token for the owner's dashboard")
    void myListingsIncludesToken() throws Exception {
        when(propertyService.getPropertiesByOwner(any(User.class))).thenReturn(List.of(property));
        authenticateAs(owner);

        mockMvc.perform(get("/api/properties/my-listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endorsementToken").value(TOKEN))
                .andExpect(jsonPath("$[0].ownerVerifyPhone").value("+254712345678"));
    }

    @Test
    @DisplayName("GET /api/properties/endorse/{token} returns the listing to whoever holds the token")
    void endorsementLookupIncludesToken() throws Exception {
        when(propertyService.getByEndorsementToken(TOKEN)).thenReturn(property);

        mockMvc.perform(get("/api/properties/endorse/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endorsementToken").value(TOKEN));
    }

    @Test
    @DisplayName("POST /api/properties/endorse/{token} returns the endorsed listing to the token holder")
    void endorseResponseIncludesToken() throws Exception {
        when(propertyService.endorseListing(eq(TOKEN), eq("Mama Wanjiku"), eq("+254712345678")))
                .thenReturn(property);

        mockMvc.perform(post("/api/properties/endorse/" + TOKEN)
                        .contentType("application/json")
                        .content("{\"verifierName\":\"Mama Wanjiku\",\"verifierPhone\":\"+254712345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landlordEndorsed").value(true))
                .andExpect(jsonPath("$.endorsementToken").value(TOKEN));
    }

    @Test
    @DisplayName("owner mutations (availability toggle) keep the token so the dashboard doesn't lose the share link")
    void ownerAvailabilityToggleKeepsToken() throws Exception {
        when(propertyService.getPropertyById(anyLong())).thenReturn(property);
        when(propertyService.setAvailability(eq(100L), eq(false))).thenReturn(property);
        authenticateAs(owner);

        mockMvc.perform(patch("/api/properties/100/availability")
                        .contentType("application/json")
                        .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endorsementToken").value(TOKEN));
    }
}
