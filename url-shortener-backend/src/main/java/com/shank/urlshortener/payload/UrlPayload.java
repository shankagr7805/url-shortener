package com.shank.urlshortener.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDateTime;

public class UrlPayload {

    // ── Request: shorten a URL ────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShortenRequest {

        @NotBlank(message = "URL must not be blank")
        @Pattern(
            regexp = "^(https?://).*",
            message = "URL must start with http:// or https://"
        )
        private String originalUrl;

        // Optional: custom expiry in days (null = never expires)
        private Integer expiryDays;

        // Optional: custom alias (null = auto-generate)
        private String customAlias;
    }

    // ── Response: shortened URL details ──────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShortenResponse {
        private String originalUrl;
        private String shortUrl;
        private String shortCode;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private Long clickCount;
    }

    // ── Response: analytics ───────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnalyticsResponse {
        private String shortCode;
        private String originalUrl;
        private Long totalClicks;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private boolean active;
    }

    // ── Response: rate limit error ────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
        private int status;
    }
}
