package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.EventAnalyticsDTO;
import com.example.dbeventservice.dto.PaginatedResponse;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.*;
import lombok.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events")
public class EventController {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketTierRepository ticketTierRepository;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    private ReunionGuestRepository reunionGuestRepository;



    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String folder = "uploads/";
            String filename = UUID.randomUUID() + "-" + file.getOriginalFilename();
            Path path = Paths.get(folder + filename);

            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());

            String baseUrl = "http://localhost:8080";
            String imageUrl = baseUrl + "/uploads/" + filename;

            System.out.println("✅ Image uploaded: " + imageUrl);
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (IOException e) {
            System.err.println("❌ Failed to upload image: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Image upload failed"));
        }
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<?> getEventAnalytics(@PathVariable Long id, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Optional<Event> optionalEvent = eventRepository.findById(id);
        if (optionalEvent.isEmpty()) return ResponseEntity.notFound().build();

        Event event = optionalEvent.get();

        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        List<Ticket> tickets = ticketRepository.findByEventId(id);

        int sold = (int) tickets.stream().filter(t -> t.getStatus().name().equals("PAID")).count();

        int available = event.getTicketTiers().stream()
                .mapToInt(TicketTier::getAvailableQuantity)
                .sum();

        double revenue = tickets.stream()
                .filter(t -> t.getStatus().name().equals("PAID"))
                .mapToDouble(t -> t.getTicketTier().getPrice() * t.getQuantity())
                .sum();

        Map<String, Long> salesByDate = tickets.stream()
                .filter(t -> t.getStatus().name().equals("PAID"))
                .filter(t -> t.getEvent().getId().equals(id))
                .collect(Collectors.groupingBy(
                        t -> t.getEvent().getDateTime().toLocalDate().toString(), // optionally use ticket timestamp if you store it
                        Collectors.counting()
                ));

        List<EventAnalyticsDTO.SalesData> salesOverTime = salesByDate.entrySet().stream()
                .map(e -> new EventAnalyticsDTO.SalesData(e.getKey(), e.getValue().intValue()))
                .toList();

        Map<String, Long> tierBreakdown = tickets.stream()
                .filter(t -> t.getStatus().name().equals("PAID"))
                .collect(Collectors.groupingBy(
                        t -> t.getTicketTier().getName(),
                        Collectors.counting()
                ));

        List<EventAnalyticsDTO.TierBreakdown> breakdown = tierBreakdown.entrySet().stream()
                .map(e -> new EventAnalyticsDTO.TierBreakdown(e.getKey(), e.getValue().intValue()))
                .toList();

        EventAnalyticsDTO dto = new EventAnalyticsDTO(
                event.getId(),
                event.getTitle(),
                sold,
                available,
                revenue,
                salesOverTime,
                breakdown
        );

        return ResponseEntity.ok(dto);
    }


    @PostMapping
    public ResponseEntity<?> createEvent(@AuthenticationPrincipal UserDetailsImpl userDetails, @RequestBody EventRequest req) {
        User user = userRepository.findById(userDetails.getId()).orElse(null);
        if (user == null) return ResponseEntity.badRequest().body("User not found");

        Event event = new Event();
        event.setTitle(req.title);
        event.setDescription(req.description);
        event.setLocation(req.location);
        event.setDateTime(req.dateTime);
        event.setImageUrl(req.imageUrl);
        event.setCreatedBy(user);
        event.setState(req.state);
        event.setAddress(req.address);
        event.setCountry(req.country);
        event.setLatitude(req.latitude);
        event.setLongitude(req.longitude);
        event.setZelleEmail(req.zelleEmail);
        event.setZellePhone(req.zellePhone);
        event.setAllowPagoMovil(req.allowPagoMovil);
        event.setAllowCash(req.allowCash);
        event.setPagoMovilPhone(req.pagoMovilPhone);
        event.setPagoMovilCi(req.pagoMovilCi);
        event.setPagoMovilBank(req.pagoMovilBank);
        event.setCashNote(req.cashNote);
        event.setType(req.type != null ? req.type : "event");

        if ("reunion".equalsIgnoreCase(event.getType())) {
            event.setBudgetGoal(req.budgetGoal);
            event.setBudgetPurpose(req.budgetPurpose);

            if (req.guests != null) {
                for (ReunionGuestDTO g : req.guests) {
                    ReunionGuest guest = new ReunionGuest();
                    guest.setName(g.name);
                    guest.setEmail(g.email);
                    guest.setPaid(g.paid);
                    guest.setEvent(event);
                    reunionGuestRepository.save(guest);
                    event.getReunionGuests().add(guest);
                }
            }
        }


        if (req.ticketTiers != null && !req.ticketTiers.isEmpty()) {
            int totalQty = req.ticketTiers.stream().mapToInt(t -> t.quantity).sum();
            double minPrice = req.ticketTiers.stream().mapToDouble(t -> t.price).min().orElse(0);

            event.setAvailableTickets(totalQty);
            event.setPrice(minPrice);
        }

        eventRepository.save(event);

        if (req.ticketTiers != null) {
            for (TicketTierRequest tierReq : req.ticketTiers) {
                TicketTier tier = new TicketTier();
                tier.setName(tierReq.name);
                tier.setPrice(tierReq.price);
                tier.setAvailableQuantity(tierReq.quantity);
                tier.setTierOrder(tierReq.order); // ✅ order logic
                tier.setStartTime(tierReq.startTime); // ✅ availability start
                tier.setEndTime(tierReq.endTime);     // ✅ availability end
                tier.setForceOpen(tierReq.isForceOpen());
                tier.setEvent(event);
                ticketTierRepository.save(tier);
            }
        }


        return ResponseEntity.ok(Map.of(
                "id", event.getId(),
                "createdByUsername", user.getUsername() // 👈 Add this
        ));
    }

    @GetMapping("/rsvp-attending")
    public ResponseEntity<List<Long>> getEventsUserRsvpedTo(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        String userEmail = userDetails.getEmail();

        List<Event> events = eventRepository.findAll();
        List<Long> attendingEventIds = new ArrayList<>();

        for (Event event : events) {
            if ("reunion".equalsIgnoreCase(event.getType())) {
                for (ReunionGuest guest : event.getReunionGuests()) {
                    if (guest.getEmail() != null &&
                            guest.getEmail().equalsIgnoreCase(userEmail) &&
                            guest.isAttending()) {
                        attendingEventIds.add(event.getId());
                        break;
                    }
                }
            }
        }

        return ResponseEntity.ok(attendingEventIds);
    }


    @PutMapping("/{eventId}/rsvp")
    public ResponseEntity<?> updateRsvpStatus(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long eventId,
            @RequestBody Map<String, Boolean> body
    ) {
        String userEmail = userDetails.getEmail();
        boolean attending = body.getOrDefault("attending", false);

        Optional<Event> optionalEvent = eventRepository.findById(eventId);
        if (optionalEvent.isEmpty()) return ResponseEntity.notFound().build();

        Event event = optionalEvent.get();
        List<ReunionGuest> guests = event.getReunionGuests();

        for (ReunionGuest guest : guests) {
            if (guest.getEmail().equalsIgnoreCase(userEmail)) {
                guest.setAttending(attending);

                if (attending) {
                    // Also add user to event's attendees set
                    Optional<User> optionalUser = userRepository.findByEmail(userEmail);
                    optionalUser.ifPresent(user -> event.getAttendees().add(user));
                }

                reunionGuestRepository.save(guest); // Save guest directly
                eventRepository.save(event);        // Save event with updated attendees
                return ResponseEntity.ok("RSVP updated");
            }
        }

        return ResponseEntity.status(403).body("Not a guest");
    }


    @GetMapping
    public ResponseEntity<?> getAllEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateTime").descending());
        Page<Event> pageEvents = eventRepository.findAll(pageable);

        PaginatedResponse<Event> response = PaginatedResponse.<Event>builder()
                .content(pageEvents.getContent())
                .currentPage(pageEvents.getNumber())
                .pageSize(pageEvents.getSize())
                .totalItems(pageEvents.getTotalElements())
                .totalPages(pageEvents.getTotalPages())
                .last(pageEvents.isLast())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEvent(@PathVariable Long id) {
        return eventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id,
            @RequestBody EventRequest req) {

        return eventRepository.findById(id).map(event -> {
            if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Unauthorized");
            }

            // 📝 Update main event fields
            event.setTitle(req.title);
            event.setDescription(req.description);
            event.setLocation(req.location);
            event.setDateTime(req.dateTime);
            event.setImageUrl(req.imageUrl);
            event.setState(req.state);
            event.setAddress(req.address);
            event.setCountry(req.country);
            event.setLatitude(req.latitude);
            event.setLongitude(req.longitude);

            // 🎟 Update ticket tiers (replace all)
            if (req.ticketTiers != null) {
                Set<TicketTier> updatedTiers = new HashSet<>();

                for (EventController.TicketTierRequest tierReq : req.ticketTiers) {
                    TicketTier tier;

                    if (tierReq.id != null && tierReq.id != 0) {
                        // try to fetch existing tier
                        tier = ticketTierRepository.findById(tierReq.id).orElse(new TicketTier());
                    } else {
                        tier = new TicketTier();
                    }

                    tier.setName(tierReq.name);
                    tier.setPrice(tierReq.price);
                    tier.setAvailableQuantity(tierReq.quantity);
                    tier.setTierOrder(tierReq.order);
                    tier.setStartTime(tierReq.startTime);
                    tier.setEndTime(tierReq.endTime);
                    tier.setForceOpen(tierReq.forceOpen);
                    tier.setEvent(event);
                    event.setZelleEmail(req.zelleEmail);
                    event.setZellePhone(req.zellePhone);
                    event.setAllowPagoMovil(req.allowPagoMovil);
                    event.setAllowCash(req.allowCash);
                    event.setPagoMovilPhone(req.pagoMovilPhone);
                    event.setPagoMovilCi(req.pagoMovilCi);
                    event.setPagoMovilBank(req.pagoMovilBank);
                    event.setCashNote(req.cashNote);
                    updatedTiers.add(tier);
                }

                event.getTicketTiers().clear(); // keep same reference
                event.getTicketTiers().addAll(updatedTiers);
            }

            eventRepository.save(event);
            return ResponseEntity.ok(event); // ✅ Send back updated event
        }).orElse(ResponseEntity.notFound().build());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable Long id) {
        return eventRepository.findById(id).map(event -> {
            if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Unauthorized");
            }

            eventRepository.delete(event);
            return ResponseEntity.ok("Event deleted");
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/attend")
    public ResponseEntity<?> attendEvent(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable Long id) {
        User user = userRepository.findById(userDetails.getId()).orElse(null);
        if (user == null) return ResponseEntity.badRequest().body("User not found");

        return eventRepository.findById(id).map(event -> {
            event.getAttendees().add(user);
            eventRepository.save(event);
            return ResponseEntity.ok("Event joined");
        }).orElse(ResponseEntity.notFound().build());
    }

    @Getter
    @Setter
    static class TicketTierRequest {
        public Long id;
        public String name;
        public double price;
        public int quantity;
        private int order;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean forceOpen;
    }

    @Getter
    @Setter
    static class EventRequest {
        public String title;
        public String description;
        public String location;
        public String state;
        public String address;
        public String country;
        public Double latitude;
        public Double longitude;
        public LocalDateTime dateTime;
        public String imageUrl;
        public List<TicketTierRequest> ticketTiers;
        public String type;

        public String budgetPurpose;
        public Double budgetGoal;
        public String zelleEmail;
        public String zellePhone;
        public List<ReunionGuestDTO> guests;

        public boolean allowPagoMovil;
        public boolean allowCash;

        public String pagoMovilPhone;
        public String pagoMovilCi;
        public String pagoMovilBank;

        public String cashNote;
    }

    @Getter
    @Setter
    static class ReunionGuestDTO {
        public String name;
        public String email;
        public boolean paid;
    }

}
