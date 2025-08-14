package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EventStatsDTO {
    private int totalEvents;     // distinct events with PAID tickets
    private Double totalSpent;   // sum of ticket prices (nullable if none)
    private Double avgPrice;     // average ticket price (nullable if none)
    private String topVenue;     // most-attended venue name (nullable)
}
