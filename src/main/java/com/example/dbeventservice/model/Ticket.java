package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.TicketStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    private String qrCodeUrl;

    @Column(nullable = false)
    private boolean paid = false;

    private LocalDateTime usedAt;
    private String scannedBy; // (email/username of the checker)

    @Column(name = "proof_url", length = 512)
    private String proofUrl;

    @Column(name = "proof_uploaded_at")
    private LocalDateTime proofUploadedAt;

    @Column
    private String paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User owner;

    @Column(length = 30)
    private String cedula;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Event event;

    @Column(nullable = false)
    private boolean used = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_tier_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private TicketTier ticketTier;
}
