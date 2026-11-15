package com.roost.controller;

import com.roost.exception.ApiException;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.service.PropertyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for the admin document-review endpoints. Same
 * standaloneSetup + SecurityContextHolder pattern as the rest of the
 * controller suite.
 */
class AdminControllerDocumentReviewTest {

    private static final String DOC_URL = "https://docs.example.com/id-front.jpg";

    private PropertyService propertyService;
    private MockMvc mockMvc;

    private User adminUser;
    private User tenantUser;
    private Property listing;

    @BeforeEach
    void setUp() {
        propertyService = mock(PropertyService.class);
        AdminController controller = new AdminController(propertyService, mock(UserRepository.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        adminUser = new User();
        adminUser.setId(99L);
        adminUser.setRole(Role.ADMIN);

        tenantUser = new User();
        tenantUser.setId(10L);
        tenantUser.setRole(Role.TENANT);

        User owner = new User();
        owner.setId(5L);
        owner.setRole(Role.LANDLORD);

        listing = new Property();
        listing.setId(100L);
        listing.setTitle("Sunny Bedsitter");
        listing.setOwner(owner);
        listing.setDocumentUrls(new ArrayList<>(List.of(DOC_URL)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    @DisplayName("GET /pending-documents returns the queue to an admin, with the document links")
    void adminSeesPendingDocuments() throws Exception {
        loginAs(adminUser);
        when(propertyService.getPendingDocumentReview()).thenReturn(List.of(listing));

        mockMvc.perform(get("/api/admin/pending-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].documentUrls[0]").value(DOC_URL));
    }

    @Test
    @DisplayName("GET /pending-documents is 403 for a non-admin and never reads the queue")
    void tenantCannotSeePendingDocuments() throws Exception {
        loginAs(tenantUser);

        mockMvc.perform(get("/api/admin/pending-documents"))
                .andExpect(status().isForbidden());

        verify(propertyService, never()).getPendingDocumentReview();
    }

    @Test
    @DisplayName("GET /pending-documents is 401 without a logged-in user")
    void anonymousCannotSeePendingDocuments() throws Exception {
        mockMvc.perform(get("/api/admin/pending-documents"))
                .andExpect(status().isUnauthorized());

        verify(propertyService, never()).getPendingDocumentReview();
    }

    @Test
    @DisplayName("POST /accept-documents lets an admin accept and returns the listing as verified")
    void adminAcceptsDocuments() throws Exception {
        loginAs(adminUser);
        listing.setDocumentVerified(true);
        when(propertyService.acceptDocuments(100L)).thenReturn(listing);

        mockMvc.perform(post("/api/admin/properties/100/accept-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentVerified").value(true));

        verify(propertyService).acceptDocuments(100L);
    }

    @Test
    @DisplayName("POST /accept-documents is 403 for a non-admin and accepts nothing")
    void tenantCannotAcceptDocuments() throws Exception {
        loginAs(tenantUser);

        mockMvc.perform(post("/api/admin/properties/100/accept-documents"))
                .andExpect(status().isForbidden());

        verify(propertyService, never()).acceptDocuments(100L);
    }

    @Test
    @DisplayName("POST /accept-documents is 401 without a logged-in user")
    void anonymousCannotAcceptDocuments() throws Exception {
        mockMvc.perform(post("/api/admin/properties/100/accept-documents"))
                .andExpect(status().isUnauthorized());

        verify(propertyService, never()).acceptDocuments(100L);
    }

    @Test
    @DisplayName("POST /accept-documents surfaces the service's 400 when the listing has no documents")
    void acceptWithoutDocumentsIsBadRequest() throws Exception {
        loginAs(adminUser);
        when(propertyService.acceptDocuments(7L))
                .thenThrow(ApiException.badRequest("This listing has no documents to accept."));

        mockMvc.perform(post("/api/admin/properties/7/accept-documents"))
                .andExpect(status().isBadRequest());
    }
}
