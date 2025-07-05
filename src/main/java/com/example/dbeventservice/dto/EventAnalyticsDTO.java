package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventAnalyticsDTO {
    private Long eventId;
    private String title;
    private int totalTicketsSold;
    private int totalTicketsAvailable;
    private double revenue;
    private List<SalesData> salesOverTime;
    private List<TierBreakdown> ticketTierBreakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesData {
        private String date;
        private int sold;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierBreakdown {
        private String tier;
        private int sold;
    }
}
