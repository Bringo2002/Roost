package com.roost.controller;

import com.roost.exception.ApiException;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.GpsReading;
import com.roost.service.PropertyRiskService;
import com.roost.service.PropertyService;
import com.roost.service.RateLimiterService;
import com.roost.service.RentEstimateService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Failed location checks are limited per listing and per user, and every
 * attempt leaves one audit line. The limits slow down someone probing the
 * address check or the distance rule; they must never lock out an honest
 * owner because of the geocoder being down or because someone else poked
 * at their listing.
 */
class PropertyControllerVerifyGpsLimitsTest {

    private static final String BODY =
            "{\"latitude\":-1.29,\"longitude\":36.82,\"accuracyMeters\":12.5,\"capturedAtEpochMs\":1700000000000}";

    private PropertyService propertyService;
    private MockMvc mockMvc;
    private User owner;
    private User stranger;
    private Logger controllerLog;
    private Handler captureHandler;
    private final List<String> logged = new ArrayList<>();

    @BeforeEach
    void setUp() {
        propertyService = mock(PropertyService.class);
        PropertyController controller = new PropertyController(
                propertyService, mock(PropertyRiskService.class),
                mock(RentEstimateService.class), new RateLimiterService());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        owner = new User();
        owner.setId(1L);
        owner.setRole(Role.LANDLORD);
        stranger = new User();
        stranger.setId(2L);
        stranger.setRole(Role.TENANT);

        for (long id = 100L; id <= 103L; id++) {
            when(propertyService.getPropertyById(id)).thenReturn(propertyWithId(id));
        }

        controllerLog = Logger.getLogger(PropertyController.class.getName());
        captureHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        controllerLog.addHandler(captureHandler);
    }

    @AfterEach
    void tearDown() {
        controllerLog.removeHandler(captureHandler);
        SecurityContextHolder.clearContext();
    }

    private Property propertyWithId(long id) {
        Property property = new Property();
        property.setId(id);
        property.setOwner(owner);
        property.setStatus("DRAFT");
        return property;
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private int attempt(long propertyId) throws Exception {
        return mockMvc.perform(post("/api/properties/" + propertyId + "/verify-gps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andReturn().getResponse().getStatus();
    }

    private void serviceRejects(long propertyId) {
        when(propertyService.verifyGpsLocation(eq(propertyId), any(GpsReading.class)))
                .thenThrow(ApiException.badRequest("You're about 500m from the listed location. Move closer and try again."));
    }

    @Test
    @DisplayName("the 9th failed check on one listing within the hour is refused with 429, without running the check")
    void blocksAfterEightFailuresOnOneListing() throws Exception {
        authenticateAs(owner);
        serviceRejects(100L);

        for (int i = 0; i < 8; i++) {
            assertTrue(attempt(100L) == 400, "attempt " + (i + 1) + " should be a normal rejection");
        }
        mockMvc.perform(post("/api/properties/100/verify-gps")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests());

        verify(propertyService, times(8)).verifyGpsLocation(eq(100L), any(GpsReading.class));
    }

    @Test
    @DisplayName("a geocoder outage (503) never counts toward the limit")
    void outageDoesNotCount() throws Exception {
        authenticateAs(owner);
        when(propertyService.verifyGpsLocation(eq(100L), any(GpsReading.class)))
                .thenThrow(ApiException.serviceUnavailable("We couldn't check the address just now."));

        for (int i = 0; i < 12; i++) {
            assertTrue(attempt(100L) == 503, "attempt " + (i + 1) + " should stay a 503");
        }
        verify(propertyService, times(12)).verifyGpsLocation(eq(100L), any(GpsReading.class));
    }

    @Test
    @DisplayName("a successful check clears the failures counted against that listing")
    void successResetsListingCount() throws Exception {
        authenticateAs(owner);
        serviceRejects(100L);
        for (int i = 0; i < 7; i++) {
            attempt(100L);
        }

        when(propertyService.verifyGpsLocation(eq(100L), any(GpsReading.class)))
                .thenReturn(propertyWithId(100L));
        assertTrue(attempt(100L) == 200, "the passing check should succeed");

        serviceRejects(100L);
        for (int i = 0; i < 7; i++) {
            assertTrue(attempt(100L) == 400, "failure " + (i + 1) + " after the reset should not be blocked");
        }
    }

    @Test
    @DisplayName("20 failed checks across listings block the user on any listing")
    void blocksAfterTwentyFailuresPerUser() throws Exception {
        authenticateAs(owner);
        serviceRejects(100L);
        serviceRejects(101L);
        serviceRejects(102L);
        serviceRejects(103L);

        for (int i = 0; i < 7; i++) {
            attempt(100L);
            attempt(101L);
        }
        for (int i = 0; i < 6; i++) {
            attempt(102L);
        }

        mockMvc.perform(post("/api/properties/103/verify-gps")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests());
        verify(propertyService, times(0)).verifyGpsLocation(eq(103L), any(GpsReading.class));
    }

    @Test
    @DisplayName("someone who isn't the owner is refused with 403 and cannot use up the owner's allowance")
    void strangersCannotLockOutTheOwner() throws Exception {
        authenticateAs(stranger);
        for (int i = 0; i < 30; i++) {
            assertTrue(attempt(100L) == 403, "a stranger should always get 403");
        }

        authenticateAs(owner);
        when(propertyService.verifyGpsLocation(eq(100L), any(GpsReading.class)))
                .thenReturn(propertyWithId(100L));
        assertTrue(attempt(100L) == 200, "the owner is unaffected");
    }

    @Test
    @DisplayName("each attempt logs one audit line with property, user, outcome, accuracy and reason")
    void logsOneAuditLinePerAttempt() throws Exception {
        authenticateAs(owner);
        serviceRejects(100L);
        attempt(100L);

        List<String> audit = logged.stream().filter(m -> m.contains("GPS verify attempt")).toList();
        assertTrue(audit.size() == 1, "expected exactly one audit line but got " + audit);
        String line = audit.get(0);
        assertTrue(line.contains("property=100"), line);
        assertTrue(line.contains("user=1"), line);
        assertTrue(line.contains("outcome=REJECTED"), line);
        assertTrue(line.contains("accuracyMeters=12.5"), line);
        assertTrue(line.contains("Move closer"), line);
    }

    @Test
    @DisplayName("a blocked attempt is logged as BLOCKED")
    void logsBlockedAttempts() throws Exception {
        authenticateAs(owner);
        serviceRejects(100L);
        for (int i = 0; i < 9; i++) {
            attempt(100L);
        }
        assertTrue(logged.stream().anyMatch(m -> m.contains("GPS verify attempt") && m.contains("outcome=BLOCKED")),
                "no BLOCKED audit line in " + logged);
    }
}
