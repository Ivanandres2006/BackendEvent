package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DiscountResult {
    private boolean valid;
    private String message;
    private String codeApplied;
    private int discountCents;

    /**
     * Remaining discounted tickets for this code.
     * Integer.MAX_VALUE represents unlimited.
     */
    private int remainingTickets;
}
