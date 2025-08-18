// src/main/java/com/example/dbeventservice/controller/AnalyticsController.java
package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ProfileAnalyticsDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.TicketRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics/me")
@RequiredArgsConstructor
public class AnalyticsController {

    private final TicketRepository ticketRepo;
    private final UserRepository userRepo;

    @GetMapping("/profile")
    public ResponseEntity<ProfileAnalyticsDTO> myProfile(@AuthenticationPrincipal UserDetailsImpl me) {
        var userOpt = userRepo.findById(me.getId());
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().build();
        var user = userOpt.get();

        // Use your eager method if available; otherwise fall back.
        List<Ticket> tickets;
        try {
            tickets = ticketRepo.findTicketsWithDetailsByOwnerAndStatus(user, TicketStatus.PAID);
        } catch (Exception ignored) {
            tickets = ticketRepo.findByOwnerAndStatus(user, TicketStatus.PAID);
        }
        if (tickets.isEmpty()) {
            return ResponseEntity.ok(new ProfileAnalyticsDTO(
                    0, 0d, 0d, 0d, 0, 0, null, null, null, null, null
            ));
        }

        // Totals
        int totalTickets = tickets.stream().mapToInt(Ticket::getQuantity).sum();
        double totalSpent = tickets.stream()
                .mapToDouble(t -> priceOf(t) * t.getQuantity())
                .sum();
        double avgTicket = totalTickets > 0 ? totalSpent / totalTickets : 0d;

        // Distinct events
        Map<Long, Event> events = tickets.stream()
                .map(Ticket::getEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Event::getId, e -> e, (a,b)->a));
        int distinctEvents = events.size();

        // Attended vs upcoming by event date
        LocalDateTime now = LocalDateTime.now();
        int attended = (int) events.values().stream()
                .filter(e -> e.getDateTime() != null && e.getDateTime().isBefore(now)).count();
        int attending = (int) events.values().stream()
                .filter(e -> e.getDateTime() != null && !e.getDateTime().isBefore(now)).count();

        double avgPerEvent = distinctEvents > 0 ? totalSpent / distinctEvents : 0d;

        // Top organizer (optional; fine if you don’t surface avatars yet)
        record OrgKey(Long id, String name, String avatar) {}
        Map<OrgKey,Integer> byOrg = new HashMap<>();
        for (var t : tickets) {
            var e = t.getEvent();
            var org = (e != null ? e.getCreatedBy() : null); // no organizer system required
            String name = org != null ? displayNameOf(org) : "Organizer";
            String avatar = org != null ? safe(org.getAvatarUrl()) : null;
            byOrg.merge(new OrgKey(org != null ? org.getId() : 0L, name, avatar), t.getQuantity(), Integer::sum);
        }
        OrgKey topOrg = byOrg.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        // Most-attended event (by qty)
        record EventKey(Long id, String name, String cover) {}
        Map<EventKey,Integer> byEvent = new HashMap<>();
        for (var t : tickets) {
            var e = t.getEvent(); if (e == null) continue;
            byEvent.merge(new EventKey(e.getId(), safe(e.getTitle()), safe(e.getImageUrl())), t.getQuantity(), Integer::sum);
        }
        var topEvent = byEvent.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);

        var dto = new ProfileAnalyticsDTO();
        dto.setTotalTickets(totalTickets);
        dto.setTotalSpent(r2(totalSpent));
        dto.setAverageTicketPrice(r2(avgTicket));
        dto.setAvgPerEvent(r2(avgPerEvent));
        dto.setAttended(attended);
        dto.setAttending(attending);

        if (topOrg != null) { dto.setTopSellerName(topOrg.name()); dto.setTopSellerAvatar(topOrg.avatar()); }
        if (topEvent != null) {
            dto.setMostEventName(topEvent.getKey().name());
            dto.setMostEventCover(topEvent.getKey().cover());
            dto.setTopEventCount(topEvent.getValue());
        }
        return ResponseEntity.ok(dto);
    }

    // helpers
    private static double priceOf(Ticket t) { try { return t.getTicketTier().getPrice(); } catch (Exception e) { return 0d; } }
    private static String displayNameOf(User u) {
        String n = (u.getFirstName() == null ? "" : u.getFirstName()) +
                (u.getLastName() == null ? "" : " " + u.getLastName());
        if (n.isBlank()) n = u.getUsername();
        return n.isBlank() ? "Organizer" : n.trim();
    }
    private static String safe(String s) { return (s == null || s.isBlank()) ? null : s; }
    private static Double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}
