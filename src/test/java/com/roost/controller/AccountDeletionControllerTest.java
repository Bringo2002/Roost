package com.roost.controller;

import com.roost.dto.AccountDeletionStatusDto;
import com.roost.exception.ApiException;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.AccountDeletionService;
import com.roost.service.AuthService;
import com.roost.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for the account deletion endpoints in
 * {@link UserController}: request, cancel, and status queries.
 */
class AccountDeletionControllerTest {

    private AccountDeletionService accountDeletionService;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        accountDeletionService = mock(AccountDeletionService.class);

        UserController controller = new UserController(
                mock(AuthService.class),
                mock(UserService.class),
                accountDeletionService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        user = new User();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPassword("hash");
        user.setRole(Role.TENANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── POST /api/users/me/delete-account ───────────────────────────────

    @Test
    @DisplayName("POST /me/delete-account returns 200 with scheduled status")
    void requestDeletion_returns200WithScheduledStatus() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduled = now.plusDays(14);
        when(accountDeletionService.requestDeletion(any(), eq("test@example.com"), eq("password"), eq("Leaving")))
                .thenReturn(AccountDeletionStatusDto.scheduled(now, scheduled, 14));

        mockMvc.perform(post("/api/users/me/delete-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password","reason":"Leaving"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.gracePeriodDays").value(14))
                .andExpect(jsonPath("$.deletionScheduledAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /me/delete-account returns 400 for wrong password")
    void requestDeletion_returns400ForWrongPassword() throws Exception {
        when(accountDeletionService.requestDeletion(any(), any(), any(), any()))
                .thenThrow(ApiException.badRequest("Password is incorrect"));

        mockMvc.perform(post("/api/users/me/delete-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"wrong","reason":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password is incorrect"));
    }

    @Test
    @DisplayName("POST /me/delete-account returns 400 when email is blank (Bean Validation)")
    void requestDeletion_returns400ForBlankEmail() throws Exception {
        mockMvc.perform(post("/api/users/me/delete-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"password","reason":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /me/delete-account returns 401 when unauthenticated")
    void requestDeletion_returns401ForUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/users/me/delete-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password","reason":null}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/users/me/cancel-deletion ──────────────────────────────

    @Test
    @DisplayName("POST /me/cancel-deletion returns 200 with cancelled status")
    void cancelDeletion_returns200WithCancelledStatus() throws Exception {
        when(accountDeletionService.cancelDeletion(any()))
                .thenReturn(AccountDeletionStatusDto.cancelled(14));

        mockMvc.perform(post("/api/users/me/cancel-deletion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    @DisplayName("POST /me/cancel-deletion returns 400 when no pending deletion")
    void cancelDeletion_returns400WhenNoPending() throws Exception {
        when(accountDeletionService.cancelDeletion(any()))
                .thenThrow(ApiException.badRequest("No pending account deletion to cancel"));

        mockMvc.perform(post("/api/users/me/cancel-deletion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No pending account deletion to cancel"));
    }

    // ── GET /api/users/me/deletion-status ───────────────────────────────

    @Test
    @DisplayName("GET /me/deletion-status returns 200 with current status")
    void getDeletionStatus_returns200WithStatus() throws Exception {
        when(accountDeletionService.getDeletionStatus(any()))
                .thenReturn(AccountDeletionStatusDto.none(14));

        mockMvc.perform(get("/api/users/me/deletion-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("none"))
                .andExpect(jsonPath("$.gracePeriodDays").value(14));
    }
}
