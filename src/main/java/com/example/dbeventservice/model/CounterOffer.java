package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.OfferStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private ChatRoom chatRoom;

    @ManyToOne
    private User sender;

    private double amount;

    @Enumerated(EnumType.STRING)
    private OfferStatus status;

    private LocalDateTime createdAt;
}
