package com.campusconnect.common.ratelimit;

import com.campusconnect.common.exception.RateLimitException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory fixed-window rate limiter (Story 2.5). {@link #check} increments a per-key
 * counter in the current window and throws {@link RateLimitException} once the count would exceed the
 * limit; the window resets lazily once it has elapsed. Keys are namespaced by the caller, e.g.
 * {@code "login:" + clientIp} or {@code "otp:" + email}.
 *
 * <p><b>Keyspace bound:</b> keys come from unauthenticated, partly spoofable input (client IP via
 * {@code X-Forwarded-For}, the {@code forgot} email), so the backing map is capped: once it reaches
 * {@code maxKeys}, the next {@link #check} lazily drops every entry whose window has already elapsed
 * (no background thread). This keeps the limiter from being an unbounded memory-growth vector while
 * staying purely in-memory.
 *
 * <p><b>Scope:</b> per-instance — deliberately the single-VM MVP design (no Redis in the stack). When
 * the platform scales to multiple replicas (Epic 10), this becomes a per-instance limit; a
 * Redis-backed or gateway-level limiter is the documented follow-up.
 */
@Component
public class RateLimiter {

    /** Default cap on distinct live keys before an elapsed-entry sweep runs. */
    static final int DEFAULT_MAX_KEYS = 100_000;

    /** Immutable per-key counter: when the current window expires (epoch millis) and how many hits it has. */
    private record Window(long expiresAtMillis, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxKeys;

    public RateLimiter() {
        this(DEFAULT_MAX_KEYS);
    }

    /** Test seam: a small cap makes the eviction sweep observable without inserting 100k keys. */
    RateLimiter(int maxKeys) {
        this.maxKeys = maxKeys;
    }

    /**
     * Records one hit against {@code key} and enforces {@code limit} hits per {@code window}. The first
     * {@code limit} calls within a window pass; the {@code limit+1}-th throws {@link RateLimitException}.
     */
    public void check(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();

        // Bound the keyspace: at the cap, drop entries whose window has fully elapsed (per-entry expiry,
        // so a long OTP window is never swept early by a short login window). Cheap lazy GC, no thread.
        if (windows.size() >= maxKeys) {
            windows.values().removeIf(w -> now >= w.expiresAtMillis());
        }

        // Atomic read-modify-write per key: start a fresh window if none exists or the old one elapsed.
        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || now >= existing.expiresAtMillis()) {
                return new Window(now + windowMillis, 1);
            }
            return new Window(existing.expiresAtMillis(), existing.count() + 1);
        });
        if (updated.count() > limit) {
            throw new RateLimitException("Too many requests — please try again later.");
        }
    }

    /** Test seam: number of distinct keys currently held. */
    int activeKeys() {
        return windows.size();
    }
}
