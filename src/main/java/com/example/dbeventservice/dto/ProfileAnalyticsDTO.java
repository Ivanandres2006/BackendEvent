package com.example.dbeventservice.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class ProfileAnalyticsDTO {
    private int totalTickets;             // Sum of quantities purchased (PAID)
    private Double totalSpent;            // Sum(price * qty)
    private Double averageTicketPrice;    // totalSpent / totalTickets
    private Double avgPerEvent;           // totalSpent / distinct events purchased

    private int attended;                 // distinct events in the past
    private int attending;                // distinct events upcoming

    // Optional niceties (ok if null)
    private String topSellerName;         // organizer you bought most from (fallback to username)
    private String topSellerAvatar;       // organizer avatar (if you store it)

    private String mostEventName;         // event with most tickets bought
    private String mostEventCover;        // event image
    private Integer topEventCount;        // qty for that event
}
