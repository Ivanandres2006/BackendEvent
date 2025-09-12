package com.example.dbeventservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TicketTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Double price;
    private Integer availableQuantity;

    // sequential order number (default flow)
    private Integer tierOrder;

    // If true, organizer wants it open immediately regardless of order
    @Column(name = "force_open")
    private Boolean forceOpen = false;

    // 👇 NEW: owner can hide the remaining quantity from buyers
    @Column(name = "hide_quantity", nullable = false)
    private Boolean hideQuantity = false;

    private String description;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnore
    private Event event;
}




