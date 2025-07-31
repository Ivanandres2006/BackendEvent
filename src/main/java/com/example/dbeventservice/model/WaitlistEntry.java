package com.example.dbeventservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName; // Name used to request access
    private String username; // Optional if user is logged in

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private LocalDateTime requestedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @JsonIgnore
    private Event event;

    public enum Status {
        PENDING,
        APPROVED,
        DENIED
    }
}
