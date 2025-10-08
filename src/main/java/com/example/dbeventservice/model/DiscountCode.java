// src/main/java/com/example/dbeventservice/model/DiscountCode.java
package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(
        name = "discount_codes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id","code"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiscountCode {
    public enum Type { PERCENT, AMOUNT } // AMOUNT in USD

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="event_id", nullable = false)
    private Long eventId;

    @Column(nullable=false, length=64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private Type type;

    // For PERCENT => 0..100; For AMOUNT => dollars (e.g. 5.00 means $5)
    @Column(nullable=false)
    private Double value;

    private Long appliesToTicketTierId; // null = all tiers

    private Integer maxUses;             // null = unlimited
    @Column(nullable=false) private Integer usedCount = 0;

    private Integer minQty;              // optional constraints
    private Integer maxQty;

    private Instant startsAt;
    private Instant endsAt;

    @Column(nullable=false) private Boolean active = true;

    // (optional) audit
    private Long createdByUserId;
}
