package com.example.dbeventservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReunionGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username; // 👈 NEW
    private String name;     // Optional display name
    private String email;    // Still store the email
    private boolean paid;

    @Column(nullable = false)
    private boolean attending = false;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
}

