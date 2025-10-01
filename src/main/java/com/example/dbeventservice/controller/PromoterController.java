// src/main/java/com/example/dbeventservice/controller/PromoterController.java
package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.PromoterEventView;
import com.example.dbeventservice.dto.ReferredBuyerDTO;
import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Promoter;
import com.example.dbeventservice.model.PromoterEvent;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.EventRepository;
import com.example.dbeventservice.repositories.PromoterEventRepository;
import com.example.dbeventservice.repositories.PromoterRepository;
import com.example.dbeventservice.repositories.PromoterSaleRepository;
import com.example.dbeventservice.repositories.UserRepository;
import com.example.dbeventservice.service.PromoterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
    @Autowired private PromoterSaleRepository promoterSaleRepository;

    @PostMapping("/become-me")
    public ResponseEntity<?> becomeMe(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Promoter p = promoterService.ensurePromoter(user);
        return ResponseEntity.ok(p);
    }

    // ✅ Owner assigns a promoter to an event (idempotent)
    @PostMapping(value="/{eventId}/assign", produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> assign(@PathVariable Long eventId,
                                    @RequestParam("username") String username) {
        promoterService.assignEventToPromoter(eventId, username);
        return ResponseEntity.ok(Map.of("ok", true, "username", username));
    }

    @GetMapping(value="/event/{eventId}/dashboard", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PromoterEventView>> dashboard(@PathVariable Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        List<PromoterEvent> rows = promoterEventRepository.findByEvent(event);

        List<PromoterEventView> out = rows.stream().map(pe -> {
            User u = pe.getPromoter().getUser();
            var userView = new PromoterEventView.UserSummary(
                    u.getId(),
                    u.getUsername(),
                    displayNameOf(u)
            );
            var promoterView = new PromoterEventView.PromoterSummary(
                    pe.getPromoter().getId(),
                    userView
            );
            return new PromoterEventView(
                    pe.getId(),
                    promoterView,
                    pe.getTicketsSold(),
                    pe.getGrossRevenue(),
                    pe.isCommissionEnabled(),
                    pe.getCommissionRate(),
                    pe.getCommissionEarned()
            );
        }).toList();

        return ResponseEntity.ok(out);
    }

    @GetMapping(value="/event/{eventId}/promoter/{promoterId}/buyers", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ReferredBuyerDTO>> buyers(@PathVariable Long eventId,
                                                         @PathVariable Long promoterId) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        Promoter promoter = promoterRepository.findById(promoterId).orElseThrow();
        PromoterEvent pe = promoterEventRepository.findByPromoterAndEvent(promoter, event)
                .orElseThrow();

        var out = promoterSaleRepository.findByPromoterEvent(pe).stream()
                .map(s -> new ReferredBuyerDTO(
                        s.getId(),
                        s.getBuyer().getUsername(),
                        s.getBuyer().getEmail(),
                        s.getTimestamp(),
                        s.getQuantity(),
                        s.getUnitPrice()
                ))
                .toList();
        return ResponseEntity.ok(out);
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
