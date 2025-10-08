// src/main/java/com/example/dbeventservice/dto/DiscountDTO.java
package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.DiscountCode.Type;
import lombok.Data;
import java.time.Instant;

@Data
public class DiscountDTO {
    private Long id;
    private Long eventId;
    private String code;
    private Type type;       // PERCENT or AMOUNT
    private Double value;    // percent or absolute USD
    private Long appliesToTicketTierId;
    private Integer maxUses;
    private Integer usedCount;
    private Integer minQty;
    private Integer maxQty;
    private Instant startsAt;
    private Instant endsAt;
    private Boolean active;
}