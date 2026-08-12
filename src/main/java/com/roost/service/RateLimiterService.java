package com.roost.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory sliding-window rate limiter, keyed by an arbitrary string
 * (e.g. "login:ip:1.2.3.4" or "signup:ip:1.2.3.4"). Used to throttle
 * /api/auth/login and /api/auth/signup -- see AuthController.
 *
 * Deliberately in-memory rather than Redis/DB-backed: this app runs as
 * a single Railway instance today, and in-memory needs zero new
 * infrastructure. The real trade-off, worth remembering if this ever
 * changes: counts reset on every restart/deploy, and if the app is ever
 * scaled to multiple instances, each instance tracks its own counts
 * independently -- the effective limit becomes (configured limit) x
 * (instance count), not a true global cap. At that point this should
 * move to a shared store (Redis, or a DB table) instead.
 */
@Service
public class RateLimiterService {

    private final Map<String, Deque<Long>> attemptLog = new ConcurrentHashMap<>();

    /**
     * True if `key` has already hit `maxAttempts` recorded events within
     * the trailing `window` -- i.e. the caller should be blocked. Does
     * NOT itself record anything; call record(key) separately once the
     * event you actually want to count (a failed login, a completed
     * signup) has happened.
     */
    public boolean isBlocked(String key, int maxAttempts, Duration window) {
        Deque<Long> timestamps = attemptLog.get(key);
        if (timestamps == null) {
            return false;
        }
        synchronized (timestamps) {
            evictExpired(timestamps, window);
            return timestamps.size() >= maxAttempts;
        }
    }

    /** Records one event for `key` at the current time. */
    public void record(String key) {
        Deque<Long> timestamps = attemptLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(System.currentTimeMillis());
        }
    }

    /** Clears history for `key` -- used after a successful login, so a
     *  few mistyped passwords followed by the right one don't linger
     *  toward the lockout threshold. */
    public void reset(String key) {
        attemptLog.remove(key);
    }

    private void evictExpired(Deque<Long> timestamps, Duration window) {
        long cutoff = System.currentTimeMillis() - window.toMillis();
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }
    }
}
