package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.model.Ticket;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime; // ⬅️ needed for the future check
import java.time.format.DateTimeFormatter;

@Getter
@Setter
public class TicketDTO {
    private Long id;
    private int quantity;
    private String status;
    private String qrCodeUrl;
    private String tierName;
    private String eventTitle;
    private Long eventId;
    private boolean resold;

    private String buyerFirstName;
    private String buyerLastName;
    private String buyerUsername;
    private String buyerEmail;
    private boolean paid;
    private String paymentMethod;

    private boolean used;
    private String usedAt;     // optional: ISO string for UI
    private String scannedBy;  // optional

    // 🔽 NEW
    private String eventDateTime;   // ISO string (from Event)
    private String eventCountry;    // country (from Event)
    private boolean resellEligible; // server-side computed

    private static final DateTimeFormatter IOS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public TicketDTO(Ticket ticket) { this(ticket, false); }

    public TicketDTO(Ticket ticket, boolean resold) {
        this.id = ticket.getId();
        this.quantity = ticket.getQuantity();

        // Status string is optional in client; keep it but don’t rely on it alone
        this.status = ticket.getStatus() != null ? ticket.getStatus().name() : null;

        this.qrCodeUrl = ticket.getQrCodeUrl();
        this.tierName = ticket.getTicketTier() != null ? ticket.getTicketTier().getName() : null;
        this.eventTitle = ticket.getEvent() != null ? ticket.getEvent().getTitle() : null;
        this.eventId = ticket.getEvent() != null ? ticket.getEvent().getId() : null;
        this.resold = resold;

        if (ticket.getOwner() != null) {
            this.buyerFirstName = ticket.getOwner().getFirstName();
            this.buyerLastName  = ticket.getOwner().getLastName();
            this.buyerUsername  = ticket.getOwner().getUsername();
            this.buyerEmail     = ticket.getOwner().getEmail();
        }

        this.paid = ticket.isPaid();
        this.paymentMethod = ticket.getPaymentMethod();

        this.used = ticket.isUsed();
        this.usedAt = ticket.getUsedAt() != null ? ticket.getUsedAt().toString() : null;
        this.scannedBy = ticket.getScannedBy();

        // --- Event fields ---
        var event = ticket.getEvent();
        var dt = (event != null) ? event.getDateTime() : null;
        this.eventDateTime = (dt != null) ? IOS_FMT.format(dt) : null;   // ⬅️ consistent, no fractions
        this.eventCountry  = (event != null) ? event.getCountry() : null;

        // STRICT future
        boolean future = (dt != null) && dt.isAfter(LocalDateTime.now());

        // Consider PAID/VALID OR boolean paid to avoid enum-null edge cases
        var ts = ticket.getStatus();
        boolean paidOrValid = ticket.isPaid()
                || ts == TicketStatus.PAID;

        this.resellEligible = future && !ticket.isUsed() && !resold && paidOrValid;
    }

    public static TicketDTO from(Ticket t, boolean isResold) {   // ⬅️ overload so you can pass resold flag
        return new TicketDTO(t, isResold);
    }
}
