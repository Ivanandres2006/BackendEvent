package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class TicketEmailItem {
    private Long id;
    private String qrBase64;   // <- inline QR image data
}

