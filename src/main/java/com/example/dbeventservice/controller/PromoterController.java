package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ReferredBuyerDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.*;
import com.example.dbeventservice.service.PromoterService;
import lombok.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/promoters")
public class PromoterController {

    @Autowired
    private PromoterService promoterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    // 1. Create promoter account
    @PostMapping("/create")
    public ResponseEntity<?> createPromoter(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        User user = userRepository.findByUsername(username).orElseThrow();
        return ResponseEntity.ok(promoterService.createPromoter(user));
    }

    // 2. Get my promoter profile
    @GetMapping("/me")
    public ResponseEntity<?> getMyPromoter(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return promoterService.getPromoterByUser(user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/event/{eventId}/promoter/{promoterId}/buyers")
    public ResponseEntity<?> getReferredBuyers(
            @PathVariable Long eventId,
            @PathVariable Long promoterId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return ResponseEntity.status(404).body("Event not found");

        Event event = eventOpt.get();

        // ✅ Only event owner can view this data
        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        // Find promoterEvent
        Optional<PromoterEvent> peOpt = promoterService.getPromoterEvent(promoterId, eventId);
        if (peOpt.isEmpty()) return ResponseEntity.status(404).body("PromoterEvent not found");

        PromoterEvent pe = peOpt.get();

        List<ReferredBuyerDTO> buyers = pe.getReferredSales().stream()
                .map(sale -> new ReferredBuyerDTO(
                        sale.getBuyer().getId(),
                        sale.getBuyer().getUsername(),
                        sale.getBuyer().getEmail(),
                        sale.getTimestamp()
                ))
                .toList();

        return ResponseEntity.ok(buyers);
    }


    @PostMapping("/{eventId}/assign")
    public ResponseEntity<?> assignEventToUser(
            @PathVariable Long eventId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Event event = eventRepository.findById(eventId).orElseThrow();

        // Verify only owner can assign
        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        // Validate and parse userId from payload
        String username = payload.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body("Missing or invalid 'username'");
        }

        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(404).body("User not found");
        }

        User user = optionalUser.get();

        PromoterEvent pe = promoterService.assignEventToPromoter(eventId, username);
        return ResponseEntity.ok(pe);
    }


    // 4. Get promoter event stats
    @GetMapping("/my-stats")
    public ResponseEntity<?> getMyStats(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Promoter promoter = promoterService.getPromoterByUser(user).orElseThrow();
        List<PromoterEvent> events = promoterService.getEventsByPromoter(promoter);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/my-stats/event/{eventId}")
    public ResponseEntity<?> getStatsForEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Optional<Promoter> promoterOpt = promoterService.getPromoterByUser(user);
        if (promoterOpt.isEmpty()) return ResponseEntity.status(403).body("You are not a promoter");

        Promoter promoter = promoterOpt.get();
        Optional<PromoterEvent> peOpt = promoterService.getPromoterEvent(promoter.getId(), eventId);
        if (peOpt.isEmpty()) return ResponseEntity.status(404).body("You are not a promoter for this event");

        PromoterEvent pe = peOpt.get();

        List<ReferredBuyerDTO> buyers = pe.getReferredSales().stream()
                .map(sale -> new ReferredBuyerDTO(
                        sale.getBuyer().getId(),
                        sale.getBuyer().getUsername(),
                        sale.getBuyer().getEmail(),
                        sale.getTimestamp()
                ))
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("eventId", eventId);
        response.put("referralCode", promoter.getReferralCode());
        response.put("ticketsSold", pe.getTicketsSold());
        response.put("buyers", buyers);

        return ResponseEntity.ok(response);
    }

    // 5. Event owner: get all promoters for event
    @GetMapping("/event/{eventId}/dashboard")
    public ResponseEntity<?> getEventPromoters(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        List<PromoterEvent> promoters = promoterService.getPromotersForEvent(event);
        return ResponseEntity.ok(promoters);
    }
}


