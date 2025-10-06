package com.example.dbeventservice.controller;

import com.example.dbeventservice.dto.AdminEventDTO;
import com.example.dbeventservice.dto.AdminResalePostDTO;
import com.example.dbeventservice.dto.AdminUserDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.EventRepository;
import com.example.dbeventservice.repositories.ResalePostRepository;
import com.example.dbeventservice.repositories.TicketRepository;
import com.example.dbeventservice.repositories.UserRepository;
import com.example.dbeventservice.service.BcvRateService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ResalePostRepository resalePostRepository;
    @Autowired private TicketRepository ticketRepository;

    @Autowired private BcvRateService bcv; // 👈 inject service

    // ---------- FX: Admin override ----------

    public record SetRateRequest(double rate, Integer ttlMinutes) {}

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/fx/ves-per-usd")
    public ResponseEntity<?> setFx(@RequestBody SetRateRequest body) {
        var ttl = body.ttlMinutes() != null ? Duration.ofMinutes(body.ttlMinutes()) : Duration.ofHours(12);
        bcv.setOverride(body.rate(), ttl);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "vesPerUsd", body.rate(),
                "ttlMinutes", ttl.toMinutes()
        ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/fx/ves-per-usd")
    public ResponseEntity<?> clearFx() {
        bcv.clearOverride();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    // ---------- existing endpoints (unchanged) ----------
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
    @Transactional
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
            int total = event.getTicketTiers().stream().mapToInt(TicketTier::getAvailableQuantity).sum();

            List<Ticket> paidTickets = ticketRepository.findByEventId(event.getId()).stream()
                    .filter(t -> t.getStatus().name().equals("PAID"))
                    .toList();

            int sold = paidTickets.stream().mapToInt(Ticket::getQuantity).sum();
            double revenue = paidTickets.stream().mapToDouble(t -> t.getTicketTier().getPrice() * t.getQuantity()).sum();

            return new AdminEventDTO(
                    event.getId(), event.getTitle(), event.getLocation(), total, sold, revenue
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
    @Transactional(readOnly = true)
    @GetMapping("/resale-posts")
    public ResponseEntity<List<AdminResalePostDTO>> getAllResalePosts() {
        var list = resalePostRepository.findAllWithSellerAndTicket()
                .stream()
                .map(p -> new AdminResalePostDTO(
                        p.getId(), p.getTitle(), p.getDescription(), p.getPrice(), p.isSold(),
                        p.getTicket() != null ? p.getTicket().getId() : null,
                        p.getSeller() != null ? p.getSeller().getEmail() : null,
                        p.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(list);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/resale-post/{id}")
    public ResponseEntity<?> deleteResalePost(@PathVariable Long id) {
        resalePostRepository.deleteById(id);
        return ResponseEntity.ok("Resale post deleted");
    }
}
