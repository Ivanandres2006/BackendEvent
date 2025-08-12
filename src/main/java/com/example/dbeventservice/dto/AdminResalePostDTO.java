package com.example.dbeventservice.dto;

import java.time.LocalDateTime;

public record AdminResalePostDTO(
        Long id,
        String title,
        String description,
        Double price,
        Boolean sold,
        Long ticketId,
        String sellerEmail,
        LocalDateTime createdAt
) {}
