package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "promoter_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"promoter_id", "event_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PromoterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private int ticketsSold = 0;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal grossRevenue = BigDecimal.ZERO;

    // Default 10% unless overridden
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate = new BigDecimal("0.10");

    // Only accrues when commissionEnabled == true
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal commissionEarned = BigDecimal.ZERO;

    // 🔒 Owner-controlled switch
    @Column(nullable = false)
    private boolean commissionEnabled = false;

    // When commissions were enabled (used to decide future vs retroactive)
    private LocalDateTime commissionActivatedAt;

    @OneToMany(mappedBy = "promoterEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromoterSale> referredSales = new ArrayList<>();
}
