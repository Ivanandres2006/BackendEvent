package com.example.dbeventservice.controller;

import com.example.dbeventservice.dto.EventSummaryDTO;
import com.example.dbeventservice.dto.UserSummaryDTO;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final TicketRepository ticketRepo;

    @GetMapping("/events/{eventId}/going")
    public ResponseEntity<List<UserSummaryDTO>> usersGoing(@PathVariable Long eventId) {
        var users = ticketRepo.findDistinctOwnersByEventIdAndStatus(eventId, TicketStatus.PAID)
                .stream().filter(u -> !u.isHideAttendance())
                .map(UserSummaryDTO::from).toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}/going")
    public ResponseEntity<List<EventSummaryDTO>> eventsUserGoing(@PathVariable Long userId) {
        var events = ticketRepo.findDistinctEventsByOwnerAndStatus(userId, TicketStatus.PAID)
                .stream().map(EventSummaryDTO::from).toList();
        return ResponseEntity.ok(events);
    }
}
