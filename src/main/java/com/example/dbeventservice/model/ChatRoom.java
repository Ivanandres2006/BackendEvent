package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.ChatType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "chat_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_room_direct_duo", columnNames = {"type", "event_id", "user_a_id", "user_b_id"}),
                @UniqueConstraint(name = "uq_room_resale", columnNames = {"type", "resale_post_id", "buyer_id"}),
                @UniqueConstraint(name = "uq_room_promoter_group", columnNames = {"type", "event_id"})
        },
        indexes = {
                @Index(name = "idx_chat_rooms_type", columnList = "type"),
                @Index(name = "idx_chat_rooms_updated_at", columnList = "updated_at")
        })
@Getter
@Setter
public class ChatRoom {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private ChatType type;

    // Group metadata (visible name + icon)
    @Column(name = "name", length = 160)
    private String name;

    @Column(name = "icon_url", length = 1024)
    private String iconUrl;

    // Context anchors (nullable depending on room type)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "resale_post_id")
    private Long resalePostId;

    // DIRECT dedupe: store ordered user ids to enforce uniqueness
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_a_id")
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_b_id")
    private User userB;

    // RESALE dedupe
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "buyer_id")
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    // Optional: soft delete in future
    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ChatMember> members = new HashSet<>();

    // getters/setters …
    // (Generate with your IDE)
}
