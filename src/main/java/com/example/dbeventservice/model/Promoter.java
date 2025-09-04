package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "promoters",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"referral_code"}),
                @UniqueConstraint(columnNames = {"user_id"})
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Promoter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "referral_code", nullable = false, length = 64, unique = true)
    private String referralCode;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "promoter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromoterEvent> events = new ArrayList<>();
}
