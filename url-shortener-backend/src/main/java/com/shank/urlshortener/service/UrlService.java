package com.shank.urlshortener.service;

import com.shank.urlshortener.model.ClickEvent;
import com.shank.urlshortener.model.Url;
import com.shank.urlshortener.payload.UrlPayload;
import com.shank.urlshortener.repository.ClickEventRepository;
import com.shank.urlshortener.repository.UrlRepository;
import com.shank.urlshortener.util.Base62Encoder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Base62Encoder base62Encoder;

    // Redis key prefixes
    private static final String URL_CACHE_PREFIX = "url::";
    private static final Duration URL_CACHE_TTL = Duration.ofHours(24);

    // ── SHORTEN URL ──────────────────────────────────────────

    @Transactional
    public Url shortenUrl(UrlPayload.ShortenRequest request) {
        // 1. Check for custom alias if provided
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            String alias = request.getCustomAlias().trim();
            if (urlRepository.existsByShortCode(alias)) {
                throw new IllegalArgumentException("Custom alias '" + alias + "' is already taken");
            }
            return buildAndSave(request, alias);
        }

        // 2. Auto-generate short code using Base62
        // Save first to get the auto-generated ID, then encode it
        Url url = Url.builder()
                .originalUrl(request.getOriginalUrl())
                .shortCode("TEMP") // placeholder
                .clickCount(0L)
                .active(true)
                .expiresAt(request.getExpiryDays() != null
                        ? LocalDateTime.now().plusDays(request.getExpiryDays())
                        : null)
                .build();

        url = urlRepository.save(url);

        // Encode the DB-generated ID to Base62
        String shortCode = base62Encoder.encode(url.getId());

        // Handle collision (very rare but possible with custom aliases)
        while (urlRepository.existsByShortCode(shortCode)) {
            shortCode = shortCode + "x";
        }

        url.setShortCode(shortCode);
        url = urlRepository.save(url);

        // Cache it immediately
        cacheUrl(url);
        log.info("Created short URL: {} -> {}", shortCode, request.getOriginalUrl());
        return url;
    }

    private Url buildAndSave(UrlPayload.ShortenRequest request, String shortCode) {
        Url url = Url.builder()
                .originalUrl(request.getOriginalUrl())
                .shortCode(shortCode)
                .clickCount(0L)
                .active(true)
                .expiresAt(request.getExpiryDays() != null
                        ? LocalDateTime.now().plusDays(request.getExpiryDays())
                        : null)
                .build();
        url = urlRepository.save(url);
        cacheUrl(url);
        return url;
    }

    // ── RESOLVE URL (most critical path — O(1) Redis lookup) ─

    public Optional<Url> resolveUrl(String shortCode) {
        String cacheKey = URL_CACHE_PREFIX + shortCode;

        // 1. Check Redis first — O(1) lookup
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("CACHE HIT  — {}", shortCode);
            Url url = (Url) cached;
            // Check if expired
            if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
                redisTemplate.delete(cacheKey);
                return Optional.empty();
            }
            return Optional.of(url);
        }

        // 2. Cache miss — hit DB
        log.info("CACHE MISS — {}", shortCode);
        Optional<Url> urlOpt = urlRepository.findByShortCode(shortCode);

        urlOpt.ifPresent(url -> {
            if (url.isActive() &&
                (url.getExpiresAt() == null || url.getExpiresAt().isAfter(LocalDateTime.now()))) {
                cacheUrl(url);
            }
        });

        return urlOpt.filter(url -> url.isActive() &&
                (url.getExpiresAt() == null || url.getExpiresAt().isAfter(LocalDateTime.now())));
    }

    // ── RECORD CLICK (async — doesn't slow down redirect) ────

    @Async
    @Transactional
    public void recordClick(String shortCode, HttpServletRequest request) {
        try {
            // Increment click count in DB
            urlRepository.incrementClickCount(shortCode);

            // Save click event for analytics
            ClickEvent event = ClickEvent.builder()
                    .shortCode(shortCode)
                    .ipAddress(getClientIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .build();
            clickEventRepository.save(event);

            // Invalidate cache so next read picks up new count
            redisTemplate.delete(URL_CACHE_PREFIX + shortCode);
        } catch (Exception e) {
            log.error("Failed to record click for {}: {}", shortCode, e.getMessage());
        }
    }

    // ── ANALYTICS ─────────────────────────────────────────────

    public UrlPayload.AnalyticsResponse getAnalytics(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode));

        return UrlPayload.AnalyticsResponse.builder()
                .shortCode(shortCode)
                .originalUrl(url.getOriginalUrl())
                .totalClicks(url.getClickCount())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .active(url.isActive())
                .build();
    }

    // ── LIST ALL URLs (paginated) ──────────────────────────────

    public Page<Url> listUrls(int page, int size) {
        return urlRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    // ── DELETE URL ────────────────────────────────────────────

    @Transactional
    public void deleteUrl(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode));
        url.setActive(false);
        urlRepository.save(url);
        redisTemplate.delete(URL_CACHE_PREFIX + shortCode);
        log.info("Deactivated URL: {}", shortCode);
    }

    // ── SCHEDULED: cleanup expired URLs every hour ────────────

    @Scheduled(fixedRate = 3600000) // every 1 hour
    @Transactional
    public void cleanupExpiredUrls() {
        int count = urlRepository.deactivateExpiredUrls(LocalDateTime.now());
        if (count > 0) {
            log.info("Deactivated {} expired URLs", count);
        }
    }

    // ── HELPERS ───────────────────────────────────────────────

    private void cacheUrl(Url url) {
        String cacheKey = URL_CACHE_PREFIX + url.getShortCode();
        redisTemplate.opsForValue().set(cacheKey, url, URL_CACHE_TTL);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
