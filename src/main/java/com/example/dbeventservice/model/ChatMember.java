package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.ChatMemberRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "chat_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_member_chat_user", columnNames = {"chat_id","user_id"}),
        indexes = {
                @Index(name = "idx_member_chat", columnList = "chat_id"),
                @Index(name = "idx_member_user", columnList = "user_id")
        })
@Getter
@Setter
public class ChatMember {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "chat_id", nullable = false)
    private ChatRoom chat;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ChatMemberRole role = ChatMemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    // For fast unread counts: the last moment this member viewed the room
    @Column(name = "last_read_at")
    private Instant lastReadAt;

    // getters/setters …
}
