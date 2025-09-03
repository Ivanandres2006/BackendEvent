package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResalePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // Display title
    private String description;
    private double price;

    @ManyToOne(fetch = FetchType.LAZY)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    private Ticket ticket;

    private boolean sold = false;

    private LocalDateTime createdAt = LocalDateTime.now();


    // 🔽 NEW: analytics + state
    @Builder.Default
    private boolean paused = false;

    @Builder.Default
    private long viewsCount = 0;

    @Builder.Default
    private long chatOpenCount = 0;

    private LocalDateTime soldAt;

    private String sellerZelleEmail;

    private String sellerPagoMovilPhone;
    private String sellerPagoMovilCi;
    private String sellerPagoMovilBank;

    private String sellerCashNote;

    // convenience helpers
    public void incViews() { this.viewsCount++; }
    public void incChatOpens() { this.chatOpenCount++; }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
