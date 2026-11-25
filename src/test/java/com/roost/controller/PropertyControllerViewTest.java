package com.roost.controller;

import com.roost.exception.ApiException;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.service.PropertyRiskService;
import com.roost.service.PropertyService;
import com.roost.service.RateLimiterService;
import com.roost.service.RentEstimateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/properties/{id}/view counts one view per viewer per listing per
 * window; repeats still return the listing but are not counted. Standalone
 * MockMvc (no Spring context); MockMvc's default remote address is 127.0.0.1.
 */
class PropertyControllerViewTest {

    private static final Duration WINDOW = Duration.ofMinutes(30);

    private PropertyService propertyService;
    private RateLimiterService rateLimiterService;
    private Property property;

    @BeforeEach
    void setUp() {
        propertyService = mock(PropertyService.class);
        rateLimiterService = mock(RateLimiterService.class);

        User owner = new User();
        owner.setId(3L);
        property = new Property();
        property.setId(10L);
        property.setOwner(owner);
    }

    private MockMvc mockMvcFor(User principal) {
        PropertyController controller = new PropertyController(
                propertyService, mock(PropertyRiskService.class),
                mock(RentEstimateService.class), rateLimiterService);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver(principal))
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
    @DisplayName("first view by a signed-in user is counted and recorded against that user")
    void firstViewByUserIsCounted() throws Exception {
        User viewer = new User();
        viewer.setId(7L);
        when(rateLimiterService.isBlocked("view:10:user:7", 1, WINDOW)).thenReturn(false);
        when(propertyService.incrementViewCount(10L)).thenReturn(property);

        mockMvcFor(viewer).perform(get("/api/properties/10/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(propertyService).incrementViewCount(10L);
        verify(rateLimiterService).record("view:10:user:7");
    }

    @Test
    @DisplayName("first view by an anonymous caller is keyed by IP")
    void firstViewByAnonymousIsKeyedByIp() throws Exception {
        when(rateLimiterService.isBlocked("view:10:ip:127.0.0.1", 1, WINDOW)).thenReturn(false);
        when(propertyService.incrementViewCount(10L)).thenReturn(property);

        mockMvcFor(null).perform(get("/api/properties/10/view"))
                .andExpect(status().isOk());

        verify(propertyService).incrementViewCount(10L);
        verify(rateLimiterService).record("view:10:ip:127.0.0.1");
    }

    @Test
    @DisplayName("a repeat view inside the window returns the listing without counting or recording")
    void repeatViewIsNotCounted() throws Exception {
        when(rateLimiterService.isBlocked("view:10:ip:127.0.0.1", 1, WINDOW)).thenReturn(true);
        when(propertyService.getPropertyDetail(10L)).thenReturn(property);

        mockMvcFor(null).perform(get("/api/properties/10/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(propertyService, never()).incrementViewCount(anyLong());
        verify(rateLimiterService, never()).record("view:10:ip:127.0.0.1");
    }

    @Test
    @DisplayName("an unknown listing is a 404 and leaves no rate-limit key behind")
    void unknownListingIs404AndNotRecorded() throws Exception {
        when(propertyService.incrementViewCount(99L))
                .thenThrow(ApiException.notFound("Property not found with id: 99"));

        mockMvcFor(null).perform(get("/api/properties/99/view"))
                .andExpect(status().isNotFound());

        verify(rateLimiterService, never()).record("view:99:ip:127.0.0.1");
    }
}
