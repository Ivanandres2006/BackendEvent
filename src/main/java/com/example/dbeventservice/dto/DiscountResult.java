// src/main/java/com/example/dbeventservice/dto/DiscountResult.java
package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class DiscountResult {
    private boolean valid;
    private String message;          // e.g. "10% off"
    private String codeApplied;      // normalized code or null
    private long discountCents;      // how much removed from subtotal
}