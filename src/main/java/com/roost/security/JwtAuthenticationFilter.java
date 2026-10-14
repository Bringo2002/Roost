package com.roost.security;

import com.roost.model.User;
import com.roost.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    /**
     * Only persist a fresh lastActiveAt if this much time has passed since
     * the last recorded one -- keeps "online" status accurate to roughly
     * this granularity without a database write on every single request.
     */
    private static final long ACTIVITY_UPDATE_THROTTLE_SECONDS = 30;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        final String jwt = authHeader.substring(7);
        final String userEmail;
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (RuntimeException e) {
            // Bad signature (which includes every token issued before a
            // JWT_SECRET rotation) or a malformed token. extractUsername only
            // parses and verifies -- no I/O -- so a failure here is a problem
            // with the token itself. Treat it as "no credentials": public
            // endpoints keep working and protected ones reject the request the
            // usual way, instead of the exception escaping the filter chain.
            log.debug("Ignoring unverifiable JWT: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            final UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            } catch (UsernameNotFoundException e) {
                // A valid token for an account that no longer exists. Any other
                // failure here (e.g. the database being down) is deliberately
                // not caught: that is a server error, not a bad credential.
                log.debug("Ignoring JWT for an unknown user");
                filterChain.doFilter(request, response);
                return;
            }
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
                if (userDetails instanceof User user) {
                    recordActivity(user);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void recordActivity(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastActive = user.getLastActiveAt();
        if (lastActive == null || lastActive.isBefore(now.minusSeconds(ACTIVITY_UPDATE_THROTTLE_SECONDS))) {
            user.setLastActiveAt(now);
            userRepository.save(user);
        }
    }
}
