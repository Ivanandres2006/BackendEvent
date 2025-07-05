package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;

    @Column(unique = true)
    private String email;

    @Column(name = "code_generated_at")
    private LocalDateTime codeGeneratedAt;

    @Column(name = "last_resend_at")
    private LocalDateTime lastResendAt;

    @Column(unique = true)
    private String phone;

    private String password;

    private boolean emailVerified;
    private boolean phoneVerified;

    private String verificationCode;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(unique = true, nullable = false)
    private String username;

    // ✅ NEW FIELDS
    private String externalProvider; // e.g., "google" or "apple"

    @Column(unique = true)
    private String externalId;       // unique id from the provider
}
