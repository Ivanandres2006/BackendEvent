package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.PromoterEvent;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.FollowRepository;
import com.example.dbeventservice.repositories.PromoterRepository;
import com.example.dbeventservice.repositories.TicketRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.example.dbeventservice.controller.AnalyticsController.displayNameOf;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired private UserRepository userRepository;
    @Autowired private PromoterRepository promoterRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private TicketRepository ticketRepository;

    @GetMapping("/{id}")
    public ResponseEntity<?> getPublicUser(@PathVariable Long id) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("User not found"));
        }
        User u = opt.get();

        long followersCount = followRepository.countAcceptedFollowers(u);
        long followingCount = followRepository.countAcceptedFollowing(u);
        long friendsCount   = followRepository.countFriends(u);
        long eventsCount    = ticketRepository
                .findDistinctEventsByOwnerAndStatus(u.getId(), TicketStatus.PAID)
                .size();

        boolean isPrivate = u.isHideProfile();

        PublicUserResponse dto = PublicUserResponse.builder()
                .id(u.getId())
                .username(nz(u.getUsername()))
                .displayName(displayNameOf(u))
                .avatarUrl(nz(u.getAvatarUrl()))
                .bio(isPrivate ? "" : nz(u.getBio()))
                .isPrivate(isPrivate)
                .followersCount(followersCount)
                .followingCount(followingCount)
                .friendsCount(friendsCount)
                .eventsCount(eventsCount)
                .build();

        return ResponseEntity.ok(dto);
    }

    // Optional convenience: fetch by @username for deep links
    @GetMapping("/by-username/{username}")
    public ResponseEntity<?> getPublicUserByUsername(@PathVariable String username) {
        User u = userRepository.findByUsername(username).orElse(null);
        if (u == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("User not found"));
        }
        return getPublicUser(u.getId());
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Unauthorized"));
        }

        User user = userRepository.findById(principal.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("User not found"));
        }

        // ✅ FIX: collect promoted event IDs via Promoter -> PromoterEvent -> Event
        List<Long> promotedEvents = promoterRepository.findByUser(user)
                .map(promoter -> promoter.getEvents().stream()
                        .map(PromoterEvent::getEvent)     // go through PromoterEvent
                        .filter(Objects::nonNull)
                        .map(Event::getId)
                        .distinct()
                        .toList()
                )
                .orElse(List.of());

        long followersCount = followRepository.countAcceptedFollowers(user);
        long followingCount = followRepository.countAcceptedFollowing(user);
        long friendsCount   = followRepository.countFriends(user);
        long eventsCount    = ticketRepository
                .findDistinctEventsByOwnerAndStatus(user.getId(), TicketStatus.PAID)
                .size();

        return ResponseEntity.ok(UserResponse.builder()
                .id(user.getId())
                .firstName(nz(user.getFirstName()))
                .lastName(nz(user.getLastName()))
                .email(nz(user.getEmail()))
                .phone(nz(user.getPhone()))
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .canCreateEvents(user.isCanCreateEvents())
                .username(nz(user.getUsername()))
                .cedula(nz(user.getCedula()))
                .avatarUrl(nz(user.getAvatarUrl()))
                .bio(nz(user.getBio()))
                .hideProfile(user.isHideProfile())
                .hideAttendance(user.isHideAttendance())
                .promotedEvents(promotedEvents)
                .followersCount(followersCount)
                .followingCount(followingCount)
                .friendsCount(friendsCount)
                .eventsCount(eventsCount)
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

    private static String nz(String s) { return s == null ? "" : s; }

    // ==== DTOs ====

    @Getter @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }

    @Getter @Setter
    static class UpdateProfileRequest {
        private String avatarUrl;       // optional
        private String bio;             // optional
        private Boolean hideProfile;    // optional
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

        private String cedula;

        // profile
        private String avatarUrl;
        private String bio;
        private boolean hideProfile;
        private boolean hideAttendance;

        private List<Long> promotedEvents;

        // counts
        private long followersCount;
        private long followingCount;
        private long friendsCount;
        private long eventsCount;
    }

    @Getter @Builder
    static class PublicUserResponse {
        private Long id;
        private String username;
        private String displayName;
        private String avatarUrl;
        private String bio;        // empty if private
        private boolean isPrivate; // true if hideProfile == true

        private long followersCount;
        private long followingCount;
        private long friendsCount;
        private long eventsCount;
    }
}
