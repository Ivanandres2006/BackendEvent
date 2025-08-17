package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_photos",
        indexes = {
                @Index(name = "idx_user_photos_user", columnList = "user_id,created_at"),
                @Index(name = "idx_user_photos_event", columnList = "event_id")
        })
@Getter @Setter
public class UserPhoto {
    public enum Status { APPROVED, PENDING, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.APPROVED;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
