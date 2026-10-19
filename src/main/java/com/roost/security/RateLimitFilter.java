package com.roost.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * IP-based rate limiter using the token-bucket algorithm (Bucket4j).
 *
 * <p>Two tiers:
 * <ul>
 *   <li><b>Auth endpoints</b> ({@code /api/auth/**}): 10 requests/minute —
 *       tight to prevent credential-stuffing and brute-force attacks.</li>
 *   <li><b>General API</b> (everything else): 60 requests/minute —
 *       generous enough for normal app usage, blocks scraping/abuse.</li>
 * </ul>
 *
 * <p>Per-IP buckets are stored in a Caffeine cache that evicts entries
 * after 10 minutes of inactivity, so memory is bounded even under high
 * cardinality. For multi-instance deployments, swap Caffeine for a
 * Redis-backed cache.
 *
 * <p>When a request is throttled, the filter returns {@code 429 Too Many
 * Requests} with a {@code Retry-After} header (in seconds) and a JSON body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)  // run early, before auth filters
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Auth tier: 10 requests per minute. */
    private static final int AUTH_CAPACITY = 10;
    private static final Duration AUTH_REFILL_PERIOD = Duration.ofMinutes(1);

    /** General tier: 60 requests per minute. */
    private static final int GENERAL_CAPACITY = 60;
    private static final Duration GENERAL_REFILL_PERIOD = Duration.ofMinutes(1);

    // Separate caches so auth and general don't share bucket quotas.
    private final Cache<String, Bucket> authBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    private final Cache<String, Bucket> generalBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();  // real IP thanks to forward-headers-strategy=NATIVE

        boolean isAuthEndpoint = path.startsWith("/api/auth");
        Bucket bucket = isAuthEndpoint
                ? authBuckets.get(ip, k -> createBucket(AUTH_CAPACITY, AUTH_REFILL_PERIOD))
                : generalBuckets.get(ip, k -> createBucket(GENERAL_CAPACITY, GENERAL_REFILL_PERIOD));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            log.warn("Rate limit exceeded for IP {} on {} (retry after {}s)", ip, path, retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write(
                    "{\"error\":\"Too many requests\",\"retryAfterSeconds\":" + retryAfterSeconds + "}"
            );
        }
    }

    private Bucket createBucket(int capacity, Duration refillPeriod) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, refillPeriod)
                        .build())
                .build();
    }
}
