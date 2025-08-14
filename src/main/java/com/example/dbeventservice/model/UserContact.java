package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.ContactType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "user_contacts")
public class UserContact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "owner_id")
    private User owner; // who uploaded the contact list

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ContactType type;

    @Column(name = "value_hash", nullable = false, length = 64)
    private String valueHash; // hex-encoded SHA-256

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
