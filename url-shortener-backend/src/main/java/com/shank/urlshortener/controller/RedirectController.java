package com.shank.urlshortener.controller;

import com.shank.urlshortener.model.Url;
import com.shank.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Redirect Controller — the hottest endpoint in the system.
 *
 * GET /{shortCode} → 301/302 redirect to original URL
 *
 * Performance path:
 * 1. Redis lookup (O(1), ~0.1ms) — cache hit → immediate redirect
 * 2. MySQL lookup (only on cache miss) → cache it → redirect
 * 3. Click tracking is ASYNC — doesn't block the redirect
 *
 * This is what allows <5ms response time under load.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class RedirectController {

    private final UrlService urlService;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        // Skip favicon and other browser auto-requests
        if (shortCode.equals("favicon.ico") || shortCode.startsWith("api")) {
            return ResponseEntity.notFound().build();
        }

        Optional<Url> urlOpt = urlService.resolveUrl(shortCode);

        if (urlOpt.isEmpty()) {
            log.warn("Short code not found or expired: {}", shortCode);
            return ResponseEntity.notFound().build();
        }

        Url url = urlOpt.get();

        // Record click asynchronously — doesn't slow down redirect
        urlService.recordClick(shortCode, request);

        // 302 Found — temporary redirect (doesn't cache in browser)
        // Use 301 for permanent redirects (but harder to update)
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, url.getOriginalUrl());
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
