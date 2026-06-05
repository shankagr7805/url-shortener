package com.shank.urlshortener.controller;

import com.shank.urlshortener.model.Url;
import com.shank.urlshortener.payload.UrlPayload;
import com.shank.urlshortener.service.RateLimiterService;
import com.shank.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UrlController {

    private final UrlService urlService;
    private final RateLimiterService rateLimiterService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── POST /api/v1/shorten ──────────────────────────────────
    @PostMapping("/shorten")
    public ResponseEntity<?> shorten(
            @Valid @RequestBody UrlPayload.ShortenRequest request,
            HttpServletRequest httpRequest) {

        // Rate limiting check
        String ip = getClientIp(httpRequest);
        if (!rateLimiterService.isAllowed(ip)) {
            long remaining = rateLimiterService.getRemainingRequests(ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Remaining", String.valueOf(remaining))
                    .body(new UrlPayload.ErrorResponse(
                            "RATE_LIMIT_EXCEEDED",
                            "Too many requests. Limit: 100 requests/minute.",
                            429));
        }

        try {
            Url url = urlService.shortenUrl(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                UrlPayload.ShortenResponse.builder()
                    .originalUrl(url.getOriginalUrl())
                    .shortUrl(baseUrl + "/" + url.getShortCode())
                    .shortCode(url.getShortCode())
                    .createdAt(url.getCreatedAt())
                    .expiresAt(url.getExpiresAt())
                    .clickCount(url.getClickCount())
                    .build()
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                new UrlPayload.ErrorResponse("BAD_REQUEST", e.getMessage(), 400));
        }
    }

    // ── GET /api/v1/urls (paginated list) ─────────────────────
    @GetMapping("/urls")
    public ResponseEntity<Map<String, Object>> listUrls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Url> urlPage = urlService.listUrls(page, size);
        return ResponseEntity.ok(Map.of(
            "urls", urlPage.getContent().stream().map(url ->
                UrlPayload.ShortenResponse.builder()
                    .originalUrl(url.getOriginalUrl())
                    .shortUrl(baseUrl + "/" + url.getShortCode())
                    .shortCode(url.getShortCode())
                    .createdAt(url.getCreatedAt())
                    .expiresAt(url.getExpiresAt())
                    .clickCount(url.getClickCount())
                    .build()
            ).toList(),
            "total_pages", urlPage.getTotalPages(),
            "total_elements", urlPage.getTotalElements(),
            "current_page", page
        ));
    }

    // ── GET /api/v1/analytics/{code} ─────────────────────────
    @GetMapping("/analytics/{shortCode}")
    public ResponseEntity<?> getAnalytics(@PathVariable String shortCode) {
        try {
            return ResponseEntity.ok(urlService.getAnalytics(shortCode));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── DELETE /api/v1/urls/{code} ────────────────────────────
    @DeleteMapping("/urls/{shortCode}")
    public ResponseEntity<?> deleteUrl(@PathVariable String shortCode) {
        try {
            urlService.deleteUrl(shortCode);
            return ResponseEntity.ok(Map.of("message", "URL deactivated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── GET /api/v1/health ────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "URL Shortener"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
