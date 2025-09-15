package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.MessageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "chat_messages",
        indexes = {
                @Index(name = "idx_msg_chat_created", columnList = "chat_id, created_at DESC"),
                @Index(name = "idx_msg_sender", columnList = "sender_id")
        })
@Getter
@Setter
public class ChatMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "chat_id", nullable = false)
    private ChatRoom chat;

    // null for SYSTEM messages
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING) @Column(name = "type", nullable = false, length = 16)
    private MessageType type = MessageType.TEXT;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    // CDN/Signed URL for images/attachments
    @Column(name = "attachment_url", length = 2048)
    private String attachmentUrl;

    // Optional structured metadata (JSON as TEXT; Postgres JSONB if you prefer)
    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // getters/setters …
}
