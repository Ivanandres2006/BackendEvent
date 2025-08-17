package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_badges",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_key"}))
@Getter @Setter
public class UserBadge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_key", referencedColumnName = "key", nullable = false)
    private Badge badge;

    @Column(name = "awarded_at", nullable = false)
    private OffsetDateTime awardedAt = OffsetDateTime.now();

    @Column
    private String reason;
}
