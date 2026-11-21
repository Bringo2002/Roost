package com.roost.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the RateLimitFilter enforces per-IP token bucket limits (and a
 * separate per-user bucket for authenticated chat traffic) and returns
 * proper 429 responses with Retry-After headers.
 */
class RateLimitFilterTest {

    private static final String SECRET = base64("0123456789abcdef0123456789abcdef");
    private static final String OTHER_SECRET = base64("fedcba9876543210fedcba9876543210");

    private final JwtService jwtService = jwtServiceWith(SECRET);
    private final RateLimitFilter filter = new RateLimitFilter(jwtService);

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

    // -- chat tier: per verified user, separate from the per-IP buckets ------

    @Test
    void chatEndpoint_authenticatedUser_allows240Requests() throws Exception {
        String token = tokenFor("chat-a@example.com", jwtService);
        for (int i = 0; i < 240; i++) {
            assertEquals(200, fire("GET", "/api/chat/history/5", "10.1.0.1", token).getStatus(),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void chatEndpoint_authenticatedUser_blocks241stRequest() throws Exception {
        String token = tokenFor("chat-b@example.com", jwtService);
        for (int i = 0; i < 240; i++) {
            fire("GET", "/api/chat/history/5", "10.1.0.2", token);
        }
        MockHttpServletResponse response = fire("GET", "/api/chat/history/5", "10.1.0.2", token);
        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
    }

    @Test
    void chatEndpoint_sendMessagePath_isInTheChatTier() throws Exception {
        // POST /api/chat (no trailing segment) sends a message; it must
        // not fall into the 60/minute general tier.
        String token = tokenFor("chat-c@example.com", jwtService);
        for (int i = 0; i < 100; i++) {
            assertEquals(200, fire("POST", "/api/chat", "10.1.0.3", token).getStatus(),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void chatTraffic_doesNotUseUpTheGeneralBucket() throws Exception {
        String token = tokenFor("chat-d@example.com", jwtService);
        for (int i = 0; i < 241; i++) {
            fire("GET", "/api/chat/unread-count", "10.1.0.4", token);
        }
        assertEquals(429, fire("GET", "/api/chat/unread-count", "10.1.0.4", token).getStatus());

        assertEquals(200, fire("/api/properties", "10.1.0.4").getStatus());
    }

    @Test
    void generalTraffic_doesNotUseUpTheChatBucket() throws Exception {
        String token = tokenFor("chat-e@example.com", jwtService);
        for (int i = 0; i < 60; i++) {
            fire("GET", "/api/properties", "10.1.0.5", token);
        }
        assertEquals(429, fire("GET", "/api/properties", "10.1.0.5", token).getStatus());

        assertEquals(200, fire("GET", "/api/chat/unread-count", "10.1.0.5", token).getStatus());
    }

    @Test
    void usersBehindOneIp_haveIndependentChatBuckets() throws Exception {
        String tokenA = tokenFor("chat-f@example.com", jwtService);
        String tokenB = tokenFor("chat-g@example.com", jwtService);
        for (int i = 0; i < 241; i++) {
            fire("GET", "/api/chat/history/5", "10.1.0.6", tokenA);
        }
        assertEquals(429, fire("GET", "/api/chat/history/5", "10.1.0.6", tokenA).getStatus());

        assertEquals(200, fire("GET", "/api/chat/history/5", "10.1.0.6", tokenB).getStatus());
    }

    @Test
    void chatEndpoint_withoutAToken_usesTheGeneralIpLimit() throws Exception {
        for (int i = 0; i < 60; i++) {
            assertEquals(200, fire("/api/chat/unread-count", "10.1.0.7").getStatus(),
                    "Request " + (i + 1) + " should be allowed");
        }
        assertEquals(429, fire("/api/chat/unread-count", "10.1.0.7").getStatus());
    }

    @Test
    void chatEndpoint_withAnUnverifiableToken_usesTheGeneralIpLimit() throws Exception {
        String forged = tokenFor("chat-h@example.com", jwtServiceWith(OTHER_SECRET));
        for (int i = 0; i < 60; i++) {
            fire("GET", "/api/chat/unread-count", "10.1.0.8", forged);
        }
        assertEquals(429, fire("GET", "/api/chat/unread-count", "10.1.0.8", forged).getStatus());

        for (int i = 0; i < 60; i++) {
            fire("GET", "/api/chat/unread-count", "10.1.0.9", "not-a-jwt");
        }
        assertEquals(429, fire("GET", "/api/chat/unread-count", "10.1.0.9", "not-a-jwt").getStatus());
    }

    @Test
    void forgedToken_cannotDrainAnotherUsersChatBucket() throws Exception {
        String victimEmail = "chat-victim@example.com";
        String forged = tokenFor(victimEmail, jwtServiceWith(OTHER_SECRET));
        for (int i = 0; i < 300; i++) {
            fire("GET", "/api/chat/history/5", "10.1.0.10", forged);
        }

        String real = tokenFor(victimEmail, jwtService);
        assertEquals(200, fire("GET", "/api/chat/history/5", "10.1.0.11", real).getStatus());
    }

    @Test
    void lookalikePaths_arePartOfTheGeneralTier() throws Exception {
        String token = tokenFor("chat-i@example.com", jwtService);
        for (int i = 0; i < 60; i++) {
            fire("GET", "/api/chatter", "10.1.0.12", token);
        }
        assertEquals(429, fire("GET", "/api/chatter", "10.1.0.12", token).getStatus());
    }

    private MockHttpServletResponse fire(String path, String remoteAddr) throws Exception {
        return fire("GET", path, remoteAddr, null);
    }

    private MockHttpServletResponse fire(String method, String path, String remoteAddr, String bearerToken)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        if (bearerToken != null) {
            request.addHeader("Authorization", "Bearer " + bearerToken);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        return response;
    }

    private static String tokenFor(String email, JwtService signer) {
        return signer.generateToken(User.withUsername(email).password("x").authorities("USER").build());
    }

    private static JwtService jwtServiceWith(String base64Secret) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", base64Secret);
        return service;
    }

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
