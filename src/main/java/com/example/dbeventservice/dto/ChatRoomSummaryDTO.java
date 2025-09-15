// src/main/java/com/example/dbeventservice/dto/ChatRoomSummaryDTO.java
package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Enum.ChatType;

import java.time.Instant;

public class ChatRoomSummaryDTO {
    public Long id;
    public ChatType type;
    public String displayName;
    public String iconUrl;
    public String lastMessagePreview;
    public Instant lastMessageAt;
    public long unreadCount;
}

