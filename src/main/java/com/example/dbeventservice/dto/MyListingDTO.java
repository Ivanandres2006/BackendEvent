package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.ResalePost;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MyListingDTO {
    private Long id;
    private String title;
    private String eventTitle;
    private Double price;
    private boolean sold;
    private LocalDateTime createdAt;
    private int pendingManualOrders;

    public static MyListingDTO from(ResalePost p, int pendingCount) {
        return MyListingDTO.builder()
                .id(p.getId())
                .title(p.getTitle())
                .eventTitle(p.getTicket() != null && p.getTicket().getEvent()!=null ? p.getTicket().getEvent().getTitle() : null)
                .price(p.getPrice())
                .sold(p.isSold())
                .createdAt(p.getCreatedAt())
                .pendingManualOrders(pendingCount)
                .build();
    }
}
