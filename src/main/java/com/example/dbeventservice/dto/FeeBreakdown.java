// src/main/java/com/example/dbeventservice/dto/FeeBreakdown.java
package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class FeeBreakdown {
    private String currency;       // "USD"
    private long subtotalCents;    // ticket(s) only (after discount!)
    private long platformFeeCents;
    private long stripeFeeCents;
    private long serviceFeeCents;
    private long totalCents;
    private double servicePctUsed;
    private double stripePctUsed;
    private int stripeFixedCents;

    // NEW:
    private Long originalSubtotalCents;   // nullable: value before discount
    private Long discountCentsApplied;    // nullable
    private String discountCodeApplied;   // nullable
    private String discountMessage;       // nullable (e.g. "10% off")
}
