package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FeeBreakdown {
    private String currency;       // "USD"
    private long subtotalCents;    // ticket(s) only
    private long platformFeeCents; // your % fee on subtotal
    private long stripeFeeCents;   // only for card; 0 otherwise
    private long serviceFeeCents;  // platform + stripeFee (single line)
    private long totalCents;       // subtotal + serviceFee
    private double servicePctUsed; // e.g. 0.08
    private double stripePctUsed;  // e.g. 0.029
    private int stripeFixedCents;  // e.g. 30
}
