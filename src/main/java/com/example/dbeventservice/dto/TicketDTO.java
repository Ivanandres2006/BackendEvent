package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Ticket;
import lombok.Getter;
import lombok.Setter;

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
        this.resold = isResold();

        if (ticket.getOwner() != null) {
            this.buyerFirstName = ticket.getOwner().getFirstName();
            this.buyerLastName = ticket.getOwner().getLastName();
            this.buyerUsername = ticket.getOwner().getUsername();
            this.buyerEmail = ticket.getOwner().getEmail();
        }

        this.paid = ticket.isPaid(); // 🔁 You must add this field to Ticket entity
        this.paymentMethod = ticket.getPaymentMethod();
    }
}
