package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.EventStatsDTO;
import com.example.dbeventservice.dto.EventSummaryDTO;
import com.example.dbeventservice.dto.UserSummaryDTO;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.TicketRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final TicketRepository ticketRepo;
    private final UserRepository userRepo;

    @GetMapping("/events/{eventId}/going")
    public ResponseEntity<List<UserSummaryDTO>> usersGoing(@PathVariable Long eventId) {
        var users = ticketRepo.findDistinctOwnersByEventIdAndStatus(eventId, TicketStatus.PAID)
                .stream()
                .filter(u -> !u.isHideAttendance())
                .map(UserSummaryDTO::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}/going")
    public ResponseEntity<List<EventSummaryDTO>> eventsUserGoing(@PathVariable Long userId) {
        var events = ticketRepo.findDistinctEventsByOwnerAndStatus(userId, TicketStatus.PAID)
                .stream()
                .map(EventSummaryDTO::from)
                .toList();
        return ResponseEntity.ok(events);
    }

    // 🔢 Real stats for the current user (PAID tickets)
    @GetMapping("/stats")
    public ResponseEntity<EventStatsDTO> myStats(@AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        var me = userRepo.findById(principal.getId()).orElse(null);
        if (me == null) return ResponseEntity.status(404).build();

        // event + ticketTier eager via query
        var tickets = ticketRepo.findTicketsWithDetailsByOwnerAndStatus(me, TicketStatus.PAID);

        // distinct event count
        int totalEvents = (int) tickets.stream()
                .map(t -> t.getEvent() == null ? null : t.getEvent().getId())
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // price list (assumes price on TicketTier; adjust if you store it elsewhere)
        var prices = tickets.stream()
                .map(t -> t.getTicketTier() == null ? null : t.getTicketTier().getPrice()) // BigDecimal?
                .filter(Objects::nonNull)
                .toList();

        Double totalSpent = null;
        Double avgPrice = null;

        if (!prices.isEmpty()) {
            BigDecimal sum = prices.stream()
                    .map(BigDecimal::valueOf)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalSpent = sum.doubleValue();


            BigDecimal avg = sum.divide(BigDecimal.valueOf(prices.size()), 2, BigDecimal.ROUND_HALF_UP);
            avgPrice = avg.doubleValue();
        }

        // top venue by frequency
        String topVenue = tickets.stream()
                .map(t -> {
                    if (t.getEvent() == null) return null;
                    var e = t.getEvent();
                    // adjust if venue name is stored differently
                    return e.getTitle(); // fallback to event title
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        return ResponseEntity.ok(new EventStatsDTO(
                totalEvents,
                totalSpent,
                avgPrice,
                topVenue
        ));
    }
}
