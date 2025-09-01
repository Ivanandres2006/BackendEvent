package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.ResaleOrder;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResaleOrderDTO {
    private Long id;
    private Long resalePostId;
    private String postTitle;
    private Double amount;
    private String currency;
    private String method;     // "pagoMovil" | "zelle" | "cash"
    private String status;     // "PENDING" | "PAID" | "CANCELLED"

    private Long buyerId;
    private String buyerUsername;
    private String buyerName;
    private String reference;

    private LocalDateTime createdAt;

    public static ResaleOrderDTO from(ResaleOrder o) {
        return ResaleOrderDTO.builder()
                .id(o.getId())
                .resalePostId(o.getResalePost().getId())
                .postTitle(o.getResalePost().getTitle())
                .amount(o.getAmount())
                .currency(o.getCurrency())
                .method(o.getMethod()!=null ? o.getMethod().name().toLowerCase() : null)
                .status(o.getStatus()!=null ? o.getStatus().name() : null)
                .buyerId(o.getBuyer()!=null ? o.getBuyer().getId() : null)
                .buyerUsername(o.getBuyer()!=null ? o.getBuyer().getUsername() : null)
                .buyerName(o.getBuyer()!=null ? (o.getBuyer().getFirstName() + " " + o.getBuyer().getLastName()) : null)
                .reference(o.getReference())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
