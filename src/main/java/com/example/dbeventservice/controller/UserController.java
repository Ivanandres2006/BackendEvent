package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.PromoterRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired private UserRepository userRepository;
    @Autowired private PromoterRepository promoterRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Unauthorized"));
        }

        User user = userRepository.findById(principal.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MessageResponse("User not found"));
        }

        // IDs of events the user promotes
        List<Long> promotedEvents = promoterRepository.findByUser(user).stream()
                .map(p -> p.getEvent() != null ? p.getEvent().getId() : null)
                .filter(Objects::nonNull)
                .toList();

        // Return everything the iOS app expects
        return ResponseEntity.ok(UserResponse.builder()
                .id(user.getId())
                .firstName(nz(user.getFirstName()))
                .lastName(nz(user.getLastName()))
                .email(nz(user.getEmail()))
                .phone(nz(user.getPhone()))
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .canCreateEvents(user.isCanCreateEvents())
                .username(nz(user.getUsername()))
                .avatarUrl(nz(user.getAvatarUrl()))
                .bio(nz(user.getBio()))
                .hideProfile(user.isHideProfile())
                .hideAttendance(user.isHideAttendance())
                .promotedEvents(promotedEvents)
                .build());
    }

    // 🔎 Username autocomplete (kept)
    @GetMapping("/search-usernames")
    public ResponseEntity<List<String>> searchUsernames(@RequestParam String q) {
        List<String> results = userRepository.findUsernamesStartingWith(q);
        return ResponseEntity.ok(results);
    }

    // ✏️ Minimal profile update (avatar/bio/privacy)
    @PatchMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserDetailsImpl principal,
                                           @RequestBody UpdateProfileRequest body) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Unauthorized"));
        }
        User user = userRepository.findById(principal.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MessageResponse("User not found"));
        }

        if (body.getAvatarUrl() != null)      user.setAvatarUrl(body.getAvatarUrl().trim());
        if (body.getBio() != null)            user.setBio(body.getBio().trim());
        if (body.getHideProfile() != null)    user.setHideProfile(body.getHideProfile());
        if (body.getHideAttendance() != null) user.setHideAttendance(body.getHideAttendance());

        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("Profile updated"));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ==== DTOs ====

    @Getter @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }

    @Getter @Setter
    static class UpdateProfileRequest {
        private String avatarUrl;     // optional
        private String bio;           // optional
        private Boolean hideProfile;  // optional
        private Boolean hideAttendance; // optional
    }

    @Getter @Builder
    static class UserResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String role;
        private boolean canCreateEvents;
        private String username;

        // 🆕 fields your iOS reads:
        private String avatarUrl;
        private String bio;
        private boolean hideProfile;
        private boolean hideAttendance;

        private List<Long> promotedEvents;
    }
}
