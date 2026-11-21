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
 * Rate limiter using the token-bucket algorithm (Bucket4j).
 *
 * <p>Three tiers:
 * <ul>
 *   <li><b>Auth endpoints</b> ({@code /api/auth/**}): 10 requests/minute per
 *       IP — tight to prevent credential-stuffing and brute-force attacks.</li>
 *   <li><b>Chat endpoints</b> ({@code /api/chat} and {@code /api/chat/**}):
 *       240 requests/minute per <em>verified user</em>. The chat screens
 *       poll several times a minute, and users on the same mobile-carrier or
 *       Wi-Fi IP would otherwise share one 60/minute budget and see the
 *       conversation freeze. The user is taken from a JWT whose signature
 *       verifies; a missing, malformed or forged token falls back to the
 *       per-IP general tier below, so a caller can never spend another
 *       user's quota.</li>
 *   <li><b>General API</b> (everything else): 60 requests/minute per IP —
 *       generous enough for normal app usage, blocks scraping/abuse.</li>
 * </ul>
 *
 * <p>Buckets are stored in Caffeine caches that evict entries
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

    /** Chat tier: 240 requests per minute per verified user. */
    private static final int CHAT_CAPACITY = 240;
    private static final Duration CHAT_REFILL_PERIOD = Duration.ofMinutes(1);

    // Separate caches so the tiers don't share bucket quotas.
    private final Cache<String, Bucket> authBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    private final Cache<String, Bucket> generalBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    private final Cache<String, Bucket> chatBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    private final JwtService jwtService;

    /**
     * Creates the filter.
     *
     * @param jwtService used to verify the bearer token that identifies a
     *                   chat caller; no database access is involved
     */
    public RateLimitFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();  // real IP thanks to forward-headers-strategy=NATIVE

        Bucket bucket = resolveBucket(request, path, ip);

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

    /** Picks the bucket for this request: auth by IP, chat by verified user, else general by IP. */
    private Bucket resolveBucket(HttpServletRequest request, String path, String ip) {
        if (path.startsWith("/api/auth")) {
            return authBuckets.get(ip, k -> createBucket(AUTH_CAPACITY, AUTH_REFILL_PERIOD));
        }
        if (isChatPath(path)) {
            String user = verifiedSubject(request);
            if (user != null) {
                return chatBuckets.get(user, k -> createBucket(CHAT_CAPACITY, CHAT_REFILL_PERIOD));
            }
        }
        return generalBuckets.get(ip, k -> createBucket(GENERAL_CAPACITY, GENERAL_REFILL_PERIOD));
    }

    /** True for {@code /api/chat} itself (sending a message) and anything under {@code /api/chat/}. */
    private static boolean isChatPath(String path) {
        return path.equals("/api/chat") || path.startsWith("/api/chat/");
    }

    /**
     * The token subject when the request carries a bearer JWT whose
     * signature verifies, otherwise null. Never throws: an unverifiable
     * token is simply treated as anonymous.
     */
    private String verifiedSubject(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            String subject = jwtService.extractUsername(header.substring(7));
            return (subject == null || subject.isBlank()) ? null : subject;
        } catch (RuntimeException e) {
            return null;
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
