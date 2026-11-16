package com.roost.controller;

import com.roost.exception.ApiException;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.VerificationDocument;
import com.roost.service.VerificationDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc tests for the admin identity-review endpoints. */
class AdminVerificationControllerTest {

    private static final byte[] BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    private VerificationDocumentService service;
    private MockMvc mockMvc;
    private User admin;
    private User tenant;
    private User lister;

    @BeforeEach
    void setUp() {
        service = mock(VerificationDocumentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminVerificationController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new ByteArrayHttpMessageConverter(), new JacksonJsonHttpMessageConverter())
                .build();

        admin = new User();
        admin.setId(99L);
        admin.setRole(Role.ADMIN);

        tenant = new User();
        tenant.setId(10L);
        tenant.setRole(Role.TENANT);

        lister = new User();
        lister.setId(7L);
        lister.setName("Mama Wanjiku");
        lister.setRole(Role.LANDLORD);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    // ── pending queue ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /identity/pending shows an admin who is waiting, with document details and no storage key")
    void adminSeesPendingIdentities() throws Exception {
        loginAs(admin);
        VerificationDocument d = new VerificationDocument();
        d.setDocType(VerificationDocument.DocType.NATIONAL_ID_FRONT);
        d.setStatus(VerificationDocument.Status.SUBMITTED);
        d.setContentType("image/jpeg");
        d.setStorageKey("verification/11111111-1111-1111-1111-111111111111.jpg");
        when(service.listPendingIdentities()).thenReturn(List.of(lister));
        when(service.listIdentityDocuments(7L)).thenReturn(List.of(d));

        mockMvc.perform(get("/api/admin/verification/identity/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(7))
                .andExpect(jsonPath("$[0].name").value("Mama Wanjiku"))
                .andExpect(jsonPath("$[0].documents[0].docType").value("NATIONAL_ID_FRONT"))
                .andExpect(jsonPath("$[0].documents[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$[0].documents[0].storageKey").doesNotExist());
    }

    @Test
    @DisplayName("GET /identity/pending is 403 for a non-admin and 401 when signed out")
    void pendingIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/verification/identity/pending"))
                .andExpect(status().isUnauthorized());

        loginAs(tenant);
        mockMvc.perform(get("/api/admin/verification/identity/pending"))
                .andExpect(status().isForbidden());

        verify(service, never()).listPendingIdentities();
    }

    // ── file ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /documents/{id}/file returns the bytes with the stored type and no caching")
    void adminGetsTheFile() throws Exception {
        loginAs(admin);
        when(service.viewDocument(admin, 5L))
                .thenReturn(new VerificationDocumentService.ReviewFile(BYTES, "image/jpeg"));

        mockMvc.perform(get("/api/admin/verification/documents/5/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(BYTES));
    }

    @Test
    @DisplayName("GET /documents/{id}/file is 403 for a non-admin and 401 when signed out, and reads nothing")
    void fileIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/verification/documents/5/file"))
                .andExpect(status().isUnauthorized());

        loginAs(tenant);
        mockMvc.perform(get("/api/admin/verification/documents/5/file"))
                .andExpect(status().isForbidden());

        verify(service, never()).viewDocument(any(User.class), anyLong());
    }

    // ── accept ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /identity/{id}/accept lets an admin accept")
    void adminAccepts() throws Exception {
        loginAs(admin);

        mockMvc.perform(post("/api/admin/verification/identity/7/accept"))
                .andExpect(status().isNoContent());

        verify(service).acceptIdentity(admin, 7L);
    }

    @Test
    @DisplayName("POST /identity/{id}/accept is 403 for a non-admin and 401 when signed out")
    void acceptIsAdminOnly() throws Exception {
        mockMvc.perform(post("/api/admin/verification/identity/7/accept"))
                .andExpect(status().isUnauthorized());

        loginAs(tenant);
        mockMvc.perform(post("/api/admin/verification/identity/7/accept"))
                .andExpect(status().isForbidden());

        verify(service, never()).acceptIdentity(any(User.class), anyLong());
    }

    @Test
    @DisplayName("POST /identity/{id}/accept surfaces the service's 409")
    void acceptConflictIsSurfaced() throws Exception {
        loginAs(admin);
        doThrow(ApiException.conflict("This person's documents are not waiting for review."))
                .when(service).acceptIdentity(admin, 7L);

        mockMvc.perform(post("/api/admin/verification/identity/7/accept"))
                .andExpect(status().isConflict());
    }

    // ── reject ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /identity/{id}/reject passes the reason to the service")
    void adminRejectsWithReason() throws Exception {
        loginAs(admin);

        mockMvc.perform(post("/api/admin/verification/identity/7/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"The photo is blurry.\"}"))
                .andExpect(status().isNoContent());

        verify(service).rejectIdentity(admin, 7L, "The photo is blurry.");
    }

    @Test
    @DisplayName("POST /identity/{id}/reject is 403 for a non-admin and 401 when signed out")
    void rejectIsAdminOnly() throws Exception {
        String body = "{\"reason\":\"The photo is blurry.\"}";
        mockMvc.perform(post("/api/admin/verification/identity/7/reject")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        loginAs(tenant);
        mockMvc.perform(post("/api/admin/verification/identity/7/reject")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        verify(service, never()).rejectIdentity(any(User.class), anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /identity/{id}/reject surfaces the service's 400 for a missing reason")
    void rejectBadRequestIsSurfaced() throws Exception {
        loginAs(admin);
        doThrow(ApiException.badRequest("Tell the lister what to fix."))
                .when(service).rejectIdentity(admin, 7L, "");

        mockMvc.perform(post("/api/admin/verification/identity/7/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
