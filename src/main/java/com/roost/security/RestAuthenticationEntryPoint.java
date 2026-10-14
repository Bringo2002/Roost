package com.roost.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers a request that needs authentication but has none (missing, expired
 * or unverifiable token) with 401, in the same {"error": "..."} shape the rest
 * of the API uses.
 *
 * Spring's default for a stateless app with no login mechanism is an empty 403,
 * which the Flutter client reads as "not allowed" rather than "sign in again":
 * its token refresh and logout only trigger on 401.
 *
 * The body is fixed on purpose; the underlying exception text is not echoed.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Authentication required\"}");
    }
}
