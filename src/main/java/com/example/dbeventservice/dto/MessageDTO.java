package com.example.dbeventservice.dto;

import lombok.*;

import java.time.LocalDateTime;

public record MessageDTO(
        Long id,
        Long senderId,
        String content,
        LocalDateTime timestamp
){}
