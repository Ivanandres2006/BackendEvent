package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Promoter;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.PromoterRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PromoterRepository promoterRepository;


    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(new MessageResponse("User not found"));
        }

        List<Long> promotedEvents = promoterRepository.findByUser(user).stream()
                .map(p -> {
                    if (p.getEvent() != null) {
                        return p.getEvent().getId();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();


        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.isCanCreateEvents(),
                user.getUsername(),
                promotedEvents // ✅ Include this
        ));
    }

    @Getter
    @AllArgsConstructor
    static class UserResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String role;
        private boolean canCreateEvents;
        private String username;
        private List<Long> promotedEvents; // ✅ NEW
    }

    @Getter
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }
}
