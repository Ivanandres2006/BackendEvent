package com.example.dbeventservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDTO {
    private Long id;
    private Long senderId;
    private String content;
    private LocalDateTime timestamp;

    // Optional for counteroffers
    private Integer offerId;
    private Double amount;
    private String status;

    private String senderName;
}
