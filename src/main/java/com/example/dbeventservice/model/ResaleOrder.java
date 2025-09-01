package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.PaymentMethod;
import com.example.dbeventservice.model.Enum.ResaleOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResaleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resale_post_id")
    private ResalePost resalePost;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "buyer_id")
    private User buyer;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    private ResaleOrderStatus status = ResaleOrderStatus.PENDING;

    private Double amount;           // from post.getPrice()
    private String currency;         // "USD" or "VES"
    private String reference;        // buyer-provided payment reference if any

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime confirmedAt;
    // 🔽 NEW
    private String cancelReason;
    private LocalDateTime cancelledAt;

}
