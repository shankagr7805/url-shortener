package com.shank.urlshortener.service;

import com.shank.urlshortener.model.Url;
import com.shank.urlshortener.payload.UrlPayload;
import com.shank.urlshortener.repository.ClickEventRepository;
import com.shank.urlshortener.repository.UrlRepository;
import com.shank.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock private UrlRepository urlRepository;
    @Mock private ClickEventRepository clickEventRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private Base62Encoder base62Encoder;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── shortenUrl tests ──────────────────────────────────────

    @Test
    void shortenUrl_shouldSaveAndReturnUrl() {
        UrlPayload.ShortenRequest request = new UrlPayload.ShortenRequest(
                "https://www.google.com", null, null);

        Url savedUrl = Url.builder()
                .id(1L)
                .originalUrl("https://www.google.com")
                .shortCode("TEMP")
                .clickCount(0L)
                .active(true)
                .build();

        when(urlRepository.save(any(Url.class))).thenReturn(savedUrl);
        when(base62Encoder.encode(1L)).thenReturn("abc123");
        when(urlRepository.existsByShortCode("abc123")).thenReturn(false);

        Url result = urlService.shortenUrl(request);

        assertNotNull(result);
        verify(urlRepository, times(2)).save(any(Url.class));
        verify(base62Encoder).encode(1L);
    }

    @Test
    void shortenUrl_withCustomAlias_shouldUseAlias() {
        UrlPayload.ShortenRequest request = new UrlPayload.ShortenRequest(
                "https://www.google.com", null, "myalias");

        when(urlRepository.existsByShortCode("myalias")).thenReturn(false);

        Url savedUrl = Url.builder()
                .id(1L)
                .originalUrl("https://www.google.com")
                .shortCode("myalias")
                .clickCount(0L)
                .active(true)
                .build();

        when(urlRepository.save(any(Url.class))).thenReturn(savedUrl);

        Url result = urlService.shortenUrl(request);

        assertNotNull(result);
        assertEquals("myalias", result.getShortCode());
    }

    @Test
    void shortenUrl_withDuplicateCustomAlias_shouldThrow() {
        UrlPayload.ShortenRequest request = new UrlPayload.ShortenRequest(
                "https://www.google.com", null, "taken");

        when(urlRepository.existsByShortCode("taken")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> urlService.shortenUrl(request));
    }

    // ── resolveUrl tests ─────────────────────────────────────

    @Test
    void resolveUrl_shouldReturnFromCache_whenCacheHit() {
        Url cachedUrl = Url.builder()
                .shortCode("abc123")
                .originalUrl("https://www.google.com")
                .active(true)
                .build();

        when(valueOperations.get("url::abc123")).thenReturn(cachedUrl);

        Optional<Url> result = urlService.resolveUrl("abc123");

        assertTrue(result.isPresent());
        assertEquals("https://www.google.com", result.get().getOriginalUrl());
        // DB should NOT be called on cache hit
        verify(urlRepository, never()).findByShortCode(anyString());
    }

    @Test
    void resolveUrl_shouldHitDB_whenCacheMiss() {
        when(valueOperations.get("url::abc123")).thenReturn(null);

        Url dbUrl = Url.builder()
                .shortCode("abc123")
                .originalUrl("https://www.google.com")
                .active(true)
                .build();

        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(dbUrl));

        Optional<Url> result = urlService.resolveUrl("abc123");

        assertTrue(result.isPresent());
        verify(urlRepository).findByShortCode("abc123");
        // Should cache after DB hit
        verify(valueOperations).set(eq("url::abc123"), any(), any());
    }

    @Test
    void resolveUrl_shouldReturnEmpty_whenNotFound() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(urlRepository.findByShortCode("notexist")).thenReturn(Optional.empty());

        Optional<Url> result = urlService.resolveUrl("notexist");

        assertTrue(result.isEmpty());
    }

    // ── deleteUrl tests ──────────────────────────────────────

    @Test
    void deleteUrl_shouldDeactivateAndEvictCache() {
        Url url = Url.builder()
                .shortCode("abc123")
                .originalUrl("https://www.google.com")
                .active(true)
                .build();

        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));
        when(urlRepository.save(any(Url.class))).thenReturn(url);

        urlService.deleteUrl("abc123");

        assertFalse(url.isActive());
        verify(redisTemplate).delete("url::abc123");
        verify(urlRepository).save(url);
    }

    @Test
    void deleteUrl_shouldThrow_whenNotFound() {
        when(urlRepository.findByShortCode("notexist")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> urlService.deleteUrl("notexist"));
    }
}
