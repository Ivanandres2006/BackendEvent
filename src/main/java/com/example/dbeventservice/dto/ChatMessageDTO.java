package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Enum.MessageType;
import java.time.Instant;

public class ChatMessageDTO {
    public Long id;
    public Long chatId;
    public Long senderId; // null for SYSTEM
    public String senderUsername;
    public MessageType type;
    public String text;
    public String attachmentUrl;
    public String metaJson;
    public Instant createdAt;
}

