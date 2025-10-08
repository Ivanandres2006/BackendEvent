package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter @Setter
@Entity
@Table(name = "discount_code")
public class DiscountCode {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private String code;               // UPPERCASE
    @Enumerated(EnumType.STRING)
    private Type type;                 // PERCENT or AMOUNT
    private double value;              // percent or USD per ticket

    private Long appliesToTicketTierId; // nullable (all tiers)

    private Integer maxUses;          // nullable => unlimited uses
    private Integer usedCount;        // nullable or 0

    private Integer minQty;           // nullable
    private Integer maxQty;           // nullable

    private OffsetDateTime startsAt;  // nullable
    private OffsetDateTime endsAt;    // nullable

    private Boolean active;

    // --- OPTIONAL ticket cap across redemptions ---
    private Integer ticketLimit;      // null => unlimited discounted tickets
    private Integer ticketsRedeemed;  // tracks consumed discounted tickets

    public enum Type { PERCENT, AMOUNT }

    public int remainingTickets() {
        if (ticketLimit == null) return Integer.MAX_VALUE;
        int used = ticketsRedeemed == null ? 0 : ticketsRedeemed;
        return Math.max(0, ticketLimit - used);
    }

    public void addRedemption(int qty) {
        if (ticketLimit == null) return; // unlimited => no tracking
        int used = ticketsRedeemed == null ? 0 : ticketsRedeemed;
        ticketsRedeemed = used + Math.max(0, qty);
    }

    public boolean isActiveNow() {
        if (Boolean.FALSE.equals(active)) return false;
        OffsetDateTime now = OffsetDateTime.now();
        if (startsAt != null && now.isBefore(startsAt)) return false;
        if (endsAt != null && now.isAfter(endsAt)) return false;
        return true;
    }
}
