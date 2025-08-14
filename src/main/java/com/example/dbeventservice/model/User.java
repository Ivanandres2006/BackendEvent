package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.Role;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Objects;

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

    // Profile
    @Column(length = 512)
    private String avatarUrl;

    @Column(length = 160)
    private String bio;

    @Column(name = "hide_profile", nullable = false)
    private boolean hideProfile = false;

    @Column(name = "hide_attendance", nullable = false)
    private boolean hideAttendance = false;


    @Column(name = "can_create_events")
    private boolean canCreateEvents = false;

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
    private String externalId;

    @PrePersist
    public void prePersistDefaults() {
        if (avatarUrl == null) avatarUrl = "";
        if (bio == null) bio = "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}




