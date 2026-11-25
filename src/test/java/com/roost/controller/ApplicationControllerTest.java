package com.roost.controller;

import com.roost.dto.ApplicationRequestDto;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Application;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/applications: the request body is validated before the service
 * runs, so a missing propertyId or fullName is a 400 rather than a 500.
 * Standalone MockMvc (no Spring context), so binding, Bean Validation and
 * GlobalExceptionHandler are exercised for real.
 */
class ApplicationControllerTest {

    private static final String URL = "/api/applications";

    private ApplicationService applicationService;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        applicationService = mock(ApplicationService.class);
        user = new User();
        user.setId(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(new ApplicationController(applicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver(user))
                // Same pinning as the other standalone controller tests: avoid the XML converter.
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private static HandlerMethodArgumentResolver principalResolver(User principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }

    @Test
    @DisplayName("valid body -> 200, and the service receives the parsed request")
    void validBodyIsAccepted() throws Exception {
        Property property = new Property();
        property.setId(10L);
        property.setOwner(user);
        Application saved = new Application();
        saved.setProperty(property);
        saved.setApplicant(user);
        saved.setFullName("Jane Doe");
        saved.setStatus("PENDING");
        saved.setCreatedAt(LocalDateTime.now());
        when(applicationService.submitApplication(any(User.class), any(ApplicationRequestDto.class))).thenReturn(saved);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propertyId\":10,\"fullName\":\"Jane Doe\",\"monthlyIncome\":85000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<ApplicationRequestDto> captor = ArgumentCaptor.forClass(ApplicationRequestDto.class);
        verify(applicationService).submitApplication(any(User.class), captor.capture());
        assertEquals(10L, captor.getValue().getPropertyId());
        assertEquals("Jane Doe", captor.getValue().getFullName());
        assertEquals(85000.0, captor.getValue().getMonthlyIncome());
    }

    @ParameterizedTest(name = "invalid body {0} -> 400, service never called")
    @ValueSource(strings = {
            "{}",
            "{\"fullName\":\"Jane Doe\"}",
            "{\"propertyId\":10}",
            "{\"propertyId\":10,\"fullName\":\"\"}",
            "{\"propertyId\":10,\"fullName\":\"   \"}",
            "{\"propertyId\":10,\"fullName\":\"Jane Doe\",\"monthlyIncome\":-1}",
            "{\"propertyId\":\"abc\",\"fullName\":\"Jane Doe\"}"
    })
    void invalidBodyIs400(String body) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("a fullName over 255 characters -> 400, naming the field")
    void overlongFullNameIs400() throws Exception {
        String body = "{\"propertyId\":10,\"fullName\":\"" + "a".repeat(256) + "\"}";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("fullName: fullName cannot exceed 255 characters"));

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("no authenticated user -> 401, service never called")
    void unauthenticatedIs401() {
        ApplicationController controller = new ApplicationController(applicationService);
        ApplicationRequestDto request = new ApplicationRequestDto();
        request.setPropertyId(10L);
        request.setFullName("Jane Doe");

        assertEquals(401, controller.submitApplication(null, request).getStatusCode().value());
        verifyNoInteractions(applicationService);
    }
}
