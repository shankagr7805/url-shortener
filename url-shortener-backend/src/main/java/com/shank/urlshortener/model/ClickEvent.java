package com.shank.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "click_events", indexes = {
    @Index(name = "idx_click_short_code", columnList = "shortCode"),
    @Index(name = "idx_click_timestamp", columnList = "clickedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String shortCode;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime clickedAt;

    @PrePersist
    public void prePersist() {
        this.clickedAt = LocalDateTime.now();
    }
}
