package com.shank.urlshortener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Distributed Rate Limiter using Redis.
 *
 * Algorithm: Fixed Window Counter
 * - Each IP gets a counter key in Redis with 1-minute TTL
 * - Counter increments on each request
 * - If counter > MAX_REQUESTS → reject with 429
 *
 * This works across multiple server instances (stateless)
 * because Redis is shared — this is what makes it "distributed".
 *
 * Resume bullet: "Implemented distributed rate limiting via Redis
 * fixed-window counter — 100 req/min per IP"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    /**
     * Check if the given IP is within rate limit.
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String ipAddress) {
        String key = "rate_limit::" + ipAddress;

        // Increment counter atomically
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            return true; // Redis error — allow by default
        }

        // Set TTL only on first request in the window
        if (count == 1) {
            redisTemplate.expire(key, WINDOW_DURATION);
        }

        if (count > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for IP: {} — count: {}", ipAddress, count);
            return false;
        }

        return true;
    }

    /**
     * Get remaining requests for an IP in the current window.
     */
    public long getRemainingRequests(String ipAddress) {
        String key = "rate_limit::" + ipAddress;
        Object count = redisTemplate.opsForValue().get(key);
        if (count == null) return MAX_REQUESTS_PER_MINUTE;
        long current = Long.parseLong(count.toString());
        return Math.max(0, MAX_REQUESTS_PER_MINUTE - current);
    }
}
