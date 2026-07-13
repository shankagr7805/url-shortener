package com.shank.urlshortener.repository;

import com.shank.urlshortener.model.Url;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Page<Url> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Increment click count directly in DB — avoids race conditions
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    void incrementClickCount(String shortCode);

    // Find expired URLs for cleanup job
    @Query("SELECT u FROM Url u WHERE u.expiresAt IS NOT NULL AND u.expiresAt < :now AND u.active = true")
    Page<Url> findExpiredUrls(LocalDateTime now, Pageable pageable);

    // Deactivate expired URLs
    @Modifying
    @Query("UPDATE Url u SET u.active = false WHERE u.expiresAt IS NOT NULL AND u.expiresAt < :now")
    int deactivateExpiredUrls(LocalDateTime now);
}
