// src/main/java/com/example/dbeventservice/dto/DiscountDTO.java
package com.example.dbeventservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DiscountDTO {
    private Long id;
    private Long eventId;
    private String code;
    private String type;                 // "PERCENT" or "AMOUNT"

    // keep primitive double but make it optional via valuePresent
    private double value;
    private boolean valuePresent = false;  // set to true only if JSON contains "value"

    @JsonProperty("value")
    public void setValueFromJson(double v) {
        this.value = v;
        this.valuePresent = true;         // only set when client sends "value"
    }

    // regular getter for controller/service
    public double getValue() { return value; }
    public boolean isValuePresent() { return valuePresent; }

    private Long appliesToTicketTierId;
    private Integer maxUses;
    private Integer usedCount;
    private Integer minQty;
    private Integer maxQty;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private Boolean active;

    // Optional ticket cap
    private Integer ticketLimit;
    private Integer ticketsRedeemed;
}
