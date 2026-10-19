package com.roost.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the RateLimitFilter enforces per-IP token bucket limits and
 * returns proper 429 responses with Retry-After headers.
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void authEndpoint_allowsFirstTenRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = fire("/api/auth/login", "10.0.0.1");
            assertEquals(200, response.getStatus(),
                    "Request " + (i + 1) + " should be allowed");
            assertNotNull(response.getHeader("X-Rate-Limit-Remaining"));
        }
    }

    @Test
    void authEndpoint_blocksEleventhRequest() throws Exception {
        for (int i = 0; i < 10; i++) {
            fire("/api/auth/login", "10.0.0.2");
        }
        MockHttpServletResponse response = fire("/api/auth/login", "10.0.0.2");
        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("Too many requests"));
    }

    @Test
    void generalEndpoint_allows60Requests() throws Exception {
        for (int i = 0; i < 60; i++) {
            MockHttpServletResponse response = fire("/api/properties", "10.0.0.3");
            assertEquals(200, response.getStatus(),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void generalEndpoint_blocks61stRequest() throws Exception {
        for (int i = 0; i < 60; i++) {
            fire("/api/properties", "10.0.0.4");
        }
        MockHttpServletResponse response = fire("/api/properties", "10.0.0.4");
        assertEquals(429, response.getStatus());
    }

    @Test
    void differentIps_haveIndependentBuckets() throws Exception {
        // Exhaust IP A on auth
        for (int i = 0; i < 10; i++) {
            fire("/api/auth/signup", "10.0.0.5");
        }
        assertEquals(429, fire("/api/auth/signup", "10.0.0.5").getStatus());

        // IP B should still have full quota
        assertEquals(200, fire("/api/auth/signup", "10.0.0.6").getStatus());
    }

    @Test
    void authAndGeneralBuckets_areIndependent() throws Exception {
        // Exhaust auth bucket for this IP
        for (int i = 0; i < 10; i++) {
            fire("/api/auth/login", "10.0.0.7");
        }
        assertEquals(429, fire("/api/auth/login", "10.0.0.7").getStatus());

        // General bucket for same IP should still work
        assertEquals(200, fire("/api/properties", "10.0.0.7").getStatus());
    }

    private MockHttpServletResponse fire(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        return response;
    }
}
