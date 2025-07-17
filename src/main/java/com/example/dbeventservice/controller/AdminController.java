package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.AdminEventDTO;
import com.example.dbeventservice.dto.AdminUserDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.EventRepository;
import com.example.dbeventservice.repositories.ResalePostRepository;
import com.example.dbeventservice.repositories.TicketRepository;
import com.example.dbeventservice.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ResalePostRepository resalePostRepository;

    @Autowired private TicketRepository ticketRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDTO>> getAllUsers() {
        List<AdminUserDTO> users = userRepository.findAll().stream()
                .map(u -> new AdminUserDTO(
                        u.getId(),
                        u.getEmail(),
                        u.getFirstName(),
                        u.getLastName(),
                        u.isCanCreateEvents()
                ))
                .toList();

        return ResponseEntity.ok(users);
    }


    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/user/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/user/{userId}/permission")
    public ResponseEntity<?> setEventPermission(
            @PathVariable Long userId,
            @RequestParam boolean allow) {
        userRepository.updateEventCreationPermission(userId, allow);
        return ResponseEntity.ok("Permission updated");
    }


    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/events")
    public ResponseEntity<List<AdminEventDTO>> getAllEvents() {
        List<AdminEventDTO> dtoList = eventRepository.findAll().stream().map(event -> {
            int total = event.getTicketTiers().stream()
                    .mapToInt(TicketTier::getAvailableQuantity)
                    .sum();

            // Count PAID tickets sold for this event
            List<Ticket> paidTickets = ticketRepository.findByEventId(event.getId()).stream()
                    .filter(t -> t.getStatus().name().equals("PAID"))
                    .toList();

            int sold = paidTickets.stream()
                    .mapToInt(Ticket::getQuantity)
                    .sum();

            double revenue = paidTickets.stream()
                    .mapToDouble(t -> t.getTicketTier().getPrice() * t.getQuantity())
                    .sum();

            return new AdminEventDTO(
                    event.getId(),
                    event.getTitle(),
                    event.getLocation(),
                    total,
                    sold,
                    revenue
            );
        }).toList();

        return ResponseEntity.ok(dtoList);
    }



    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/event/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        eventRepository.deleteById(id);
        return ResponseEntity.ok("Event deleted");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/resale-posts")
    public ResponseEntity<List<ResalePost>> getAllResalePosts() {
        return ResponseEntity.ok(resalePostRepository.findAll());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/resale-post/{id}")
    public ResponseEntity<?> deleteResalePost(@PathVariable Long id) {
        resalePostRepository.deleteById(id);
        return ResponseEntity.ok("Resale post deleted");
    }
}
