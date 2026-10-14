package com.roost.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The filter sits in front of every request, public ones included, so its
 * contract is: a request is either authenticated or passes through with no
 * credentials -- it never fails because of what is in the Authorization header.
 *
 * Uses a real JwtService (with a test secret) rather than a mock so the tokens
 * exercised here are genuinely signed, expired or unverifiable.
 */
class JwtAuthenticationFilterTest {

    private static final String EMAIL = "tenant@example.com";
    // HS256 needs a key of at least 32 bytes; the property holds it base64-encoded.
    private static final String SECRET = base64("0123456789abcdef0123456789abcdef");

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;
    private User user;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        userDetailsService = mock(UserDetailsService.class);
        userRepository = mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, userRepository);

        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);
        user.setRole(Role.TENANT);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** A correctly signed token whose expiry is already in the past. */
    private static String expiredToken(String subject, String base64Secret) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(new Date(System.currentTimeMillis() - 7_200_000L))
                .expirationTime(new Date(System.currentTimeMillis() - 3_600_000L))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(Base64.getDecoder().decode(base64Secret)));
        return jwt.serialize();
    }

    private void bearer(String token) {
        request.addHeader("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("a request with no Authorization header passes through unauthenticated")
    void noHeaderPassesThrough() throws Exception {
        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("a non-Bearer Authorization header is ignored")
    void nonBearerHeaderIsIgnored() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("a valid token authenticates the user and records their activity")
    void validTokenAuthenticates() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(user);
        bearer(jwtService.generateToken(user));

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(userRepository).save(user);
    }

    // Regression: these three used to throw out of the filter, so a stale or
    // garbage token could break even public endpoints.

    @Test
    @DisplayName("a token signed with a different secret (e.g. after a rotation) is treated as no credentials")
    void tokenFromAnotherSecretIsIgnored() throws Exception {
        JwtService other = new JwtService();
        ReflectionTestUtils.setField(other, "secretKey", base64("fedcba9876543210fedcba9876543210"));
        bearer(other.generateToken(user));

        assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("a malformed token is treated as no credentials")
    void malformedTokenIsIgnored() {
        bearer("not-a-jwt");

        assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("a valid token for an account that no longer exists is treated as no credentials")
    void tokenForDeletedUserIsIgnored() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new UsernameNotFoundException("User not found"));
        bearer(jwtService.generateToken(user));

        assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("a failure loading the user (e.g. database down) is not mistaken for a bad token")
    void loadFailureStillPropagates() {
        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new IllegalStateException("database unavailable"));
        bearer(jwtService.generateToken(user));

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain));
    }

    @Test
    @DisplayName("an expired token is ignored and the request continues unauthenticated")
    void expiredTokenIsIgnored() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(user);
        bearer(expiredToken(EMAIL, SECRET));

        assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
