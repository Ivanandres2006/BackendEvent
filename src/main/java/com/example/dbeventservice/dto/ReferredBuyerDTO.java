package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReferredBuyerDTO {
    private Long id; // 👈 Add this
    private String username;
    private String email;
    private LocalDateTime timestamp;

    private Integer quantity;
    private java.math.BigDecimal unitPrice;
}
