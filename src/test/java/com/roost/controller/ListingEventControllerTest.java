package com.roost.controller;

import com.roost.dto.ListingEventBatchRequest;
import com.roost.dto.ListingEventBatchResponse;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.ListingEventType;
import com.roost.model.User;
import com.roost.service.ListingEventService;
import com.roost.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc (no Spring context, no database) so the request
 * binding, Bean Validation and GlobalExceptionHandler mapping are exercised
 * for real. Spring Security's @AuthenticationPrincipal resolver is not
 * present in standalone mode, so a stand-in resolver supplies the user.
 */
@ExtendWith(MockitoExtension.class)
class ListingEventControllerTest {

    private static final String URL = "/api/events/listings";

    @Mock
    private ListingEventService listingEventService;

    @Mock
    private RateLimiterService rateLimiterService;

    private ListingEventController controller;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        controller = new ListingEventController(listingEventService, rateLimiterService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver(user))
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
    @DisplayName("valid batch -> 200 with accepted/dropped, and the request is counted against the user's limit")
    void validBatchIsAccepted() throws Exception {
        when(listingEventService.record(eq(user), anyList())).thenReturn(new ListingEventBatchResponse(2, 0));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"propertyId\":1,\"type\":\"IMPRESSION\"},{\"propertyId\":2,\"type\":\"CLICK\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.dropped").value(0));

        verify(rateLimiterService).record("listing-events:user:7");
    }

    @ParameterizedTest(name = "invalid body {0} -> 400, nothing recorded")
    @ValueSource(strings = {
            "{}",
            "{\"events\":[]}",
            "{\"events\":[null]}",
            "{\"events\":[{\"type\":\"CLICK\"}]}",
            "{\"events\":[{\"propertyId\":1}]}",
            "{\"events\":[{\"propertyId\":1,\"type\":\"SWIPE\"}]}"
    })
    void invalidBodyIs400(String body) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listingEventService, rateLimiterService);
    }

    @Test
    @DisplayName("more than the max batch size -> 400, nothing recorded")
    void oversizedBatchIs400() throws Exception {
        String items = IntStream.rangeClosed(1, ListingEventBatchRequest.MAX_EVENTS + 1)
                .mapToObj(i -> "{\"propertyId\":" + i + ",\"type\":\"IMPRESSION\"}")
                .collect(Collectors.joining(","));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[" + items + "]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listingEventService, rateLimiterService);
    }

    @Test
    @DisplayName("user over the rate limit -> 429, service never called, request not counted again")
    void rateLimitedIs429() throws Exception {
        when(rateLimiterService.isBlocked(anyString(), anyInt(), any())).thenReturn(true);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"propertyId\":1,\"type\":\"CLICK\"}]}"))
                .andExpect(status().isTooManyRequests());

        verify(rateLimiterService, never()).record(anyString());
        verifyNoInteractions(listingEventService);
    }

    @Test
    @DisplayName("no authenticated user -> 401, service never called")
    void unauthenticatedIs401() {
        ListingEventBatchRequest request = new ListingEventBatchRequest(
                List.of(new ListingEventBatchRequest.Item(1L, ListingEventType.CLICK)));

        ResponseEntity<?> response = controller.recordListingEvents(request, null);

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(listingEventService, rateLimiterService);
    }
}
