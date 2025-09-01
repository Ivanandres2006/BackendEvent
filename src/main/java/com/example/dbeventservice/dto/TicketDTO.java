package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Ticket;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime; // ⬅️ needed for the future check

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

    // ✅ Constructor used when just mapping a Ticket (default fallback)
    public TicketDTO(Ticket ticket) {
        this(ticket, false); // default: not resold
    }

    public TicketDTO(Ticket ticket, boolean resold) {
        this.id = ticket.getId();
        this.quantity = ticket.getQuantity();
        this.status = ticket.getStatus().name();
        this.qrCodeUrl = ticket.getQrCodeUrl();
        this.tierName = ticket.getTicketTier().getName();
        this.eventTitle = ticket.getEvent().getTitle();
        this.eventId = ticket.getEvent().getId();
        this.resold = resold;

        if (ticket.getOwner() != null) {
            this.buyerFirstName = ticket.getOwner().getFirstName();
            this.buyerLastName  = ticket.getOwner().getLastName();
            this.buyerUsername  = ticket.getOwner().getUsername();
            this.buyerEmail     = ticket.getOwner().getEmail();
        }

        this.paid = ticket.isPaid(); // (make sure Ticket has this field)
        this.paymentMethod = ticket.getPaymentMethod();

        this.used = ticket.isUsed();
        this.usedAt = ticket.getUsedAt() != null ? ticket.getUsedAt().toString() : null;
        this.scannedBy = ticket.getScannedBy();

        // --- NEW: pull info from the Event ---
        var event = ticket.getEvent();
        var dt = (event != null) ? event.getDateTime() : null;

        this.eventDateTime = (dt != null) ? dt.toString() : null;
        this.eventCountry  = (event != null) ? event.getCountry() : null;

        boolean future = (dt != null) && dt.isAfter(LocalDateTime.now());
        this.resellEligible = future && !ticket.isUsed() && !resold;
    }
}
