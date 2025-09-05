package com.example.dbeventservice.dto;

import java.math.BigDecimal;

public record PromoterEventView(
        Long id,
        PromoterSummary promoter,
        int ticketsSold,
        BigDecimal grossRevenue,
        boolean commissionEnabled,
        BigDecimal commissionRate,
        BigDecimal commissionEarned
) {
    public record PromoterSummary(Long id, UserSummary user) {}
    public record UserSummary(Long id, String username, String fullName) {}
}
