package com.shank.urlshortener.repository;

import com.shank.urlshortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortCode(String shortCode);

    @Query("SELECT DATE(c.clickedAt) as date, COUNT(c) as count FROM ClickEvent c " +
           "WHERE c.shortCode = :shortCode AND c.clickedAt >= :since " +
           "GROUP BY DATE(c.clickedAt) ORDER BY DATE(c.clickedAt)")
    List<Object[]> findDailyClickStats(String shortCode, LocalDateTime since);
}
