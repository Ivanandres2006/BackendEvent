package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.TicketDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.*;
import com.example.dbeventservice.service.CloudinaryStorageService;
import com.example.dbeventservice.service.TicketService;
import com.example.dbeventservice.util.QRCodeGenerator;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;



import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketTierRepository ticketTierRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResalePostRepository resalePostRepository; // ✅ NEW
    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CloudinaryStorageService storageService;


    @org.springframework.beans.factory.annotation.Value("${app.event.futureGraceHours:6}")
    private long futureGraceHours;

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);


    @Autowired
    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody TicketCheckoutRequest request,
                                      @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            Map<String, Object> response = ticketService.createCheckout(
                    userDetails.getId(),
                    request.getEventId(),
                    request.getTicketTierId(),
                    request.getQuantity(),
                    request.getEmail(),
                    request.getRef(),
                    request.getPaymentMethod(),
                    request.getCedula()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/eligible")
    public ResponseEntity<?> eligible(@AuthenticationPrincipal UserDetailsImpl user) {
        var now = LocalDateTime.now(); // ⬅️ strict, no grace
        var tickets = ticketRepository.findEligibleForResale(user.getId(), now);

        var list = tickets.stream()
                .map(t -> TicketDTO.from(t, /*resold*/ false)) // query already excludes active listings
                .toList();

        return ResponseEntity.ok(list);
    }

    // mine-all / my — keep these, but pass the real resold flag:
    @GetMapping("/mine-all")
    public ResponseEntity<?> mineAll(@AuthenticationPrincipal UserDetailsImpl user) {
        var owner = userRepository.findById(user.getId()).orElseThrow();
        var list  = ticketRepository.findByOwner(owner).stream()
                .map(t -> TicketDTO.from(t, resalePostRepository.existsByTicketAndSoldFalse(t)))
                .toList();
        return ResponseEntity.ok(list);
    }




    @GetMapping("/my-event-ids")
    public ResponseEntity<?> getMyActiveEventIds(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        Optional<User> optionalUser = userRepository.findById(userDetails.getId());
        if (optionalUser.isEmpty()) return ResponseEntity.badRequest().body("User not found");

        User user = optionalUser.get();

        List<Ticket> tickets = ticketRepository.findByOwnerAndStatus(user, TicketStatus.PAID);

        Set<Long> eventIds = tickets.stream()
                .map(t -> t.getEvent().getId())
                .collect(Collectors.toSet());

        return ResponseEntity.ok(eventIds);
    }



    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPayment(@RequestParam String paymentIntentId) {
        ticketService.confirmPayment(paymentIntentId);
        return ResponseEntity.ok(Map.of("status", "confirmed"));
    }

    @GetMapping("/event/{eventId}/dashboard")
    public ResponseEntity<?> getTicketsForEventDashboard(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Event not found"));

        Event event = eventOpt.get();
        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        List<Ticket> tickets = ticketRepository.findTicketsWithDetailsByEventId(eventId);
        List<TicketDTO> result = tickets.stream()
                .map(t -> new TicketDTO(t, false))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{ticketId}/toggle-paid")
    public ResponseEntity<?> togglePaidStatus(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Ticket not found"));

        Ticket ticket = ticketOpt.get();
        Event event = ticket.getEvent();

        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            System.out.println("❌ Unauthorized toggle attempt by user: " + userDetails.getId());
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        boolean newPaidValue = !ticket.isPaid();
        ticket.setPaid(newPaidValue);
        ticket.setStatus(newPaidValue ? TicketStatus.PAID : TicketStatus.PENDING); // ✅ add this
        ticketRepository.save(ticket);

        return ResponseEntity.ok(Map.of(
                "status", "Payment status updated",
                "paid", ticket.isPaid()
        ));
    }


    @PostMapping(
            value = "/{id}/proof",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Transactional
    public ResponseEntity<?> uploadProof(
            @PathVariable Long id,
            @RequestPart(value = "file",  required = false) MultipartFile file,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @AuthenticationPrincipal UserDetailsImpl user
    ) {
        var ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (ticket.getOwner() == null || !ticket.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        MultipartFile part = (file != null && !file.isEmpty()) ? file : image;
        if (part == null || part.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }

        try {
            Long eventId = ticket.getEvent() != null ? ticket.getEvent().getId() : 0L;
            String url = storageService.uploadTicketReceipt(part, eventId, ticket.getId(), user.getId());

            ticket.setProofUrl(url);
            ticket.setProofUploadedAt(LocalDateTime.now());
            if (!ticket.isPaid()) ticket.setStatus(TicketStatus.PENDING);

            ticketRepository.saveAndFlush(ticket);

            log.info("✅ Receipt uploaded for ticket {}: {}", ticket.getId(), url);
            return ResponseEntity.ok(Map.of("ticketId", ticket.getId(), "proofUrl", url));

        } catch (org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
            return ResponseEntity.status(413).body(Map.of("error", "File too large"));
        } catch (Exception e) {
            log.warn("❌ Upload failed for ticket {}: {}", id, e.toString());
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed", "detail", e.getMessage()));
        }
    }


    @GetMapping("/my")
    public ResponseEntity<?> myTickets(@RequestParam String email) {
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        var tickets = ticketRepository.findTicketsWithDetailsByOwnerAndStatus(userOpt.get(), TicketStatus.PAID);
        var result = tickets.stream()
                .map(t -> TicketDTO.from(t, resalePostRepository.existsByTicketAndSoldFalse(t)))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/validate/{ticketId}/mark-used")
    public ResponseEntity<?> markTicketAsUsed(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Ticket not found"));
        }

        Ticket ticket = ticketOpt.get();
        Event event = ticket.getEvent();

        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        if (ticket.isUsed()) {
            return ResponseEntity.status(400).body(Map.of("error", "Ticket already marked as used"));
        }

        ticket.setUsed(true);
        ticket.setUsedAt(LocalDateTime.now());
        ticket.setScannedBy(userDetails.getUsername());
        ticketRepository.save(ticket);

        return ResponseEntity.ok(Map.of("status", "Ticket marked as used"));
    }

    @PutMapping("/validate/{ticketId}/mark-unused")
    public ResponseEntity<?> markTicketAsUnused(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Ticket not found"));
        }

        Ticket ticket = ticketOpt.get();
        Event event = ticket.getEvent();

        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        if (!ticket.isUsed()) {
            return ResponseEntity.ok(Map.of("status", "Ticket already unused"));
        }

        ticket.setUsed(false);
        ticket.setUsedAt(null);
        ticket.setScannedBy(null);
        ticketRepository.save(ticket);

        return ResponseEntity.ok(Map.of("status", "Ticket marked as unused"));
    }


    @GetMapping("/available-tier/{eventId}")
    public ResponseEntity<?> getAvailableTier(@PathVariable Long eventId) {
        LocalDateTime now = LocalDateTime.now();

        List<TicketTier> tiers = ticketTierRepository.findByEventIdOrderByTierOrderAsc(eventId);

        for (TicketTier tier : tiers) {
            int sold = ticketRepository.countByTicketTierId(tier.getId());
            boolean inTimeWindow = (tier.getStartTime() == null || now.isAfter(tier.getStartTime())) &&
                    (tier.getEndTime() == null || now.isBefore(tier.getEndTime()));

            if ((Boolean.TRUE.equals(tier.getForceOpen()) || (sold < tier.getAvailableQuantity() && inTimeWindow)))
            {
                return ResponseEntity.ok(tier);
            }
        }

        return ResponseEntity.status(404).body(Map.of("error", "No available ticket tier at this time"));
    }

    @PutMapping("/tier/{id}/force-open")
    public ResponseEntity<?> forceOpenTier(@PathVariable Long id, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Optional<TicketTier> tierOpt = ticketTierRepository.findById(id);
        if (tierOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Tier not found"));
        }

        TicketTier tier = tierOpt.get();

        // Only the event owner can open the tier
        if (!tier.getEvent().getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        tier.setForceOpen(true);
        ticketTierRepository.save(tier);

        return ResponseEntity.ok(Map.of("status", "Tier force-opened"));
    }

    @PutMapping("/tier/{id}/force-close")
    public ResponseEntity<?> forceCloseTier(@PathVariable Long id, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Optional<TicketTier> tierOpt = ticketTierRepository.findById(id);
        if (tierOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Tier not found"));
        }

        TicketTier tier = tierOpt.get();
        if (!tier.getEvent().getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        tier.setForceOpen(false);
        ticketTierRepository.save(tier);

        return ResponseEntity.ok(Map.of("status", "Tier force-closed"));
    }

    @GetMapping(value = "/qr/{ticketId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrForTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) return ResponseEntity.status(404).build();

        Ticket ticket = ticketOpt.get();

        // Allow owner or event owner to view QR
        boolean isOwner = ticket.getOwner() != null && ticket.getOwner().getId().equals(userDetails.getId());
        boolean isEventOwner = ticket.getEvent() != null && ticket.getEvent().getCreatedBy().getId().equals(userDetails.getId());
        if (!isOwner && !isEventOwner) return ResponseEntity.status(403).build();

        try {
            String qrText = "TICKET:" + ticket.getId() + ":" + ticket.getOwner().getEmail();
            byte[] png = QRCodeGenerator.generatePngBytes(qrText, 300);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                    .body(png);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }


    @GetMapping("/validate/{ticketId}")
    public ResponseEntity<?> validateTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Ticket not found"));
        }

        Ticket ticket = ticketOpt.get();
        Event event = ticket.getEvent();

        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized: You don't own this event"));
        }

        return ResponseEntity.ok(Map.of(
                "ticketId", ticket.getId(),
                "event", event.getTitle(),
                "tier", ticket.getTicketTier().getName(),
                "quantity", ticket.getQuantity(),
                "status", ticket.getStatus().name(),
                "email", ticket.getOwner().getEmail(),
                "used", ticket.isUsed()
        ));
    }

    @GetMapping(value = "/qr/public/{ticketId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrForTicketPublic(@PathVariable Long ticketId) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) return ResponseEntity.status(404).build();

        Ticket ticket = ticketOpt.get();
        try {
            String qrText = "TICKET:" + ticket.getId() + ":" + ticket.getOwner().getEmail();
            byte[] png = QRCodeGenerator.generatePngBytes(qrText, 300);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                    .body(png);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }


    @Getter
    @Setter
    public static class TicketCheckoutRequest {
        private Long eventId;
        private Long ticketTierId;
        private int quantity;
        private String email;
        private String ref;
        private String paymentMethod;

        private String cedula;
    }
}