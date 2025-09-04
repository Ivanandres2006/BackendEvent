package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ReferredBuyerDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.EventRepository;
import com.example.dbeventservice.repositories.PromoterEventRepository;
import com.example.dbeventservice.repositories.PromoterRepository;
import com.example.dbeventservice.repositories.UserRepository;
import com.example.dbeventservice.service.PromoterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.dbeventservice.controller.AnalyticsController.displayNameOf;

@RestController
@RequestMapping("/api/promoters")
public class PromoterController {

    @Autowired private PromoterService promoterService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private PromoterEventRepository promoterEventRepository;
    @Autowired private PromoterRepository promoterRepository;

    @PostMapping("/become-me")
    public ResponseEntity<?> becomeMe(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Promoter p = promoterService.ensurePromoter(user);
        return ResponseEntity.ok(p);
    }

    @PostMapping("/{eventId}/assign")
    public ResponseEntity<?> assignEvent(
            @PathVariable Long eventId,
            @RequestParam String username,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Unauthorized");
        }
        PromoterEvent pe = promoterService.assignEventToPromoter(eventId, username);
        return ResponseEntity.ok(pe);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Optional<Promoter> p = promoterService.getPromoterByUser(user);
        return p.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body("Not a promoter"));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<?> getEventPromoters(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Unauthorized");
        }
        List<PromoterEvent> promoters = promoterService.getPromotersForEvent(event);
        return ResponseEntity.ok(promoters);
    }

    @GetMapping("/buyers/{eventId}")
    public ResponseEntity<?> myBuyers(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        User me = userRepository.findById(userDetails.getId()).orElseThrow();
        Promoter promoter = promoterRepository.findByUser(me).orElseThrow();
        promoterEventRepository.findByPromoterAndEvent(
                promoter, eventRepository.findById(eventId).orElseThrow()
        ).orElseThrow();

        List<ReferredBuyerDTO> buyers = promoterService.getReferredBuyers(promoter.getId(), eventId);
        return ResponseEntity.ok(buyers);
    }

    // 🔑 OWNER: enable/disable commission and/or adjust rate, optional retroactive inclusion
    @PatchMapping("/{eventId}/commission")
    public ResponseEntity<?> updateCommission(
            @PathVariable Long eventId,
            @RequestParam String username,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) BigDecimal rate,
            @RequestParam(defaultValue = "false") boolean retroactive,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Unauthorized");
        }
        PromoterEvent pe = promoterService.updateCommission(eventId, username, enabled, rate, retroactive);
        return ResponseEntity.ok(pe);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam String q) {
        String term = q.trim();
        if (term.isEmpty()) return ResponseEntity.ok(List.of());

        // Use A or B depending on the repo method you added
        List<User> found = userRepository
                .findTop10ByUsernameStartingWithIgnoreCaseOrFirstNameStartingWithIgnoreCaseOrLastNameStartingWithIgnoreCase(
                        term, term, term
                );

        List<com.example.dbeventservice.dto.UserSearchDTO> out = found.stream()
                .map(u -> new com.example.dbeventservice.dto.UserSearchDTO(
                        u.getId(),
                        u.getUsername() == null ? "" : u.getUsername(),
                        displayNameOf(u)
                ))
                .toList();

        return ResponseEntity.ok(out);
    }

}
