package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        // Optional: Fetch from DB to be up-to-date
        User user = userRepository.findById(userDetails.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(new MessageResponse("User not found"));
        }

        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.getUsername()
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
        private String username;
    }

    @Getter
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }
}
