package com.roost.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.service.PropertyService;
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

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for the user disable/lock admin endpoints added in M2.
 * Uses the same standaloneSetup + SecurityContextHolder pattern as the rest of
 * the controller test suite (no spring-security-test on the classpath).
 */
class AdminControllerUserStateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UserRepository userRepository;
    private MockMvc mockMvc;

    private User adminUser;
    private User tenantUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        PropertyService propertyService = mock(PropertyService.class);

        AdminController controller = new AdminController(propertyService, userRepository);

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

        targetUser = new User();
        targetUser.setId(42L);
        targetUser.setRole(Role.TENANT);
        targetUser.setEnabled(true);
        targetUser.setAccountNonLocked(true);

        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    // ── disable endpoint ──────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/admin/users/{id}/enabled with false disables the user")
    void disableUser() throws Exception {
        loginAs(adminUser);

        mockMvc.perform(patch("/api/admin/users/42/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{id}/enabled with true re-enables the user")
    void enableUser() throws Exception {
        loginAs(adminUser);
        targetUser.setEnabled(false);

        mockMvc.perform(patch("/api/admin/users/42/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("non-admin tenant gets 403 when trying to disable a user")
    void nonAdminCannotDisable() throws Exception {
        loginAs(tenantUser);

        mockMvc.perform(patch("/api/admin/users/42/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).save(any());
    }

    // ── lock endpoint ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/admin/users/{id}/locked with true locks the user")
    void lockUser() throws Exception {
        loginAs(adminUser);

        mockMvc.perform(patch("/api/admin/users/42/locked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("locked", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{id}/locked with false unlocks the user")
    void unlockUser() throws Exception {
        loginAs(adminUser);
        targetUser.setAccountNonLocked(false);

        mockMvc.perform(patch("/api/admin/users/42/locked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("locked", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    @DisplayName("missing 'enabled' field returns 400")
    void missingEnabledFieldIs400() throws Exception {
        loginAs(adminUser);

        mockMvc.perform(patch("/api/admin/users/42/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
