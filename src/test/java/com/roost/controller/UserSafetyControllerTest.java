package com.roost.controller;

import com.roost.dto.BlockedUserDto;
import com.roost.exception.ApiException;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.UserBlock;
import com.roost.service.UserSafetyService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc tests for the block, unblock, blocked-list and report endpoints. */
class UserSafetyControllerTest {

    private UserSafetyService service;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        service = mock(UserSafetyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserSafetyController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        user = new User();
        user.setId(1L);
        user.setName("Me");
        user.setEmail("me@example.com");
        user.setPassword("hash");
        user.setRole(Role.TENANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /{id}/block blocks the user and says so")
    void blockReturnsBlockedTrue() throws Exception {
        mockMvc.perform(post("/api/users/5/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));

        verify(service).blockUser(any(User.class), eq(5L));
    }

    @Test
    @DisplayName("DELETE /{id}/block unblocks the user and says so")
    void unblockReturnsBlockedFalse() throws Exception {
        mockMvc.perform(delete("/api/users/5/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));

        verify(service).unblockUser(any(User.class), eq(5L));
    }

    @Test
    @DisplayName("GET /me/blocked lists the blocked users")
    void blockedListIsReturned() throws Exception {
        User other = new User();
        other.setId(7L);
        other.setName("Other");
        when(service.getBlockedUsers(any(User.class)))
                .thenReturn(List.of(BlockedUserDto.from(new UserBlock(user, other))));

        mockMvc.perform(get("/api/users/me/blocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].name").value("Other"));
    }

    @Test
    @DisplayName("blocking without being signed in is a 401")
    void blockRequiresSignIn() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/users/5/block")).andExpect(status().isUnauthorized());

        verify(service, never()).blockUser(any(), any());
    }

    @Test
    @DisplayName("an ApiException from the service keeps its status (blocking yourself is a 400)")
    void serviceErrorsKeepTheirStatus() throws Exception {
        doThrow(ApiException.badRequest("You can't block yourself"))
                .when(service).blockUser(any(User.class), eq(1L));

        mockMvc.perform(post("/api/users/1/block")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/report with a reason and a note is accepted")
    void reportIsAccepted() throws Exception {
        mockMvc.perform(post("/api/users/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"HARASSMENT\",\"note\":\"keeps insulting me\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true));

        verify(service).reportUser(any(User.class), eq(5L), any());
    }

    @Test
    @DisplayName("a report without a note is accepted")
    void reportWithoutNoteIsAccepted() throws Exception {
        mockMvc.perform(post("/api/users/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a report without a reason is a 400")
    void reportWithoutReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/users/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"hello\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).reportUser(any(), any(), any());
    }

    @Test
    @DisplayName("a report with an unknown reason is a 400")
    void reportWithUnknownReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/users/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"BECAUSE\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).reportUser(any(), any(), any());
    }

    @Test
    @DisplayName("a report with a note over 500 characters is a 400")
    void reportWithOverlongNoteIsRejected() throws Exception {
        String note = "x".repeat(501);

        mockMvc.perform(post("/api/users/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"note\":\"" + note + "\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).reportUser(any(), any(), any());
    }

    @Test
    @DisplayName("a duplicate report keeps the service's 409")
    void duplicateReportIsAConflict() throws Exception {
        doThrow(ApiException.conflict("You've already reported this user"))
                .when(service).reportUser(any(User.class), eq(5L), any());

        mockMvc.perform(post("/api/users/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("reporting without being signed in is a 401")
    void reportRequiresSignIn() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/users/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }
}
