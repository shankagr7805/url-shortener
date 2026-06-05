package com.shank.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "urls", indexes = {
    @Index(name = "idx_short_code", columnList = "shortCode"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Url implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false, unique = true, length = 10)
    private String shortCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // null means never expires
    @Column
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private Long clickCount = 0L;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
