// src/main/java/com/example/dbeventservice/model/Follow.java
package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.FollowStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(name = "follows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id","followee_id"}))
public class Follow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id")
    private User follower;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id")
    private User followee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FollowStatus status = FollowStatus.PENDING;

    // ↓ Three layers of safety so it NEVER goes in as NULL
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null)    status = FollowStatus.PENDING;
    }
}
