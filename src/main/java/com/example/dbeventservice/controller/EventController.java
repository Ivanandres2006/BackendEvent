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
import com.example.dbeventservice.model.WaitlistEntry;


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

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;


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

    @PostMapping("/{eventId}/waitlist/request")
    public ResponseEntity<?> requestWaitlist(
            @PathVariable Long eventId,
            @RequestParam String fullName,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Event> optEvent = eventRepository.findById(eventId);
        if (optEvent.isEmpty()) return ResponseEntity.notFound().build();
        Event event = optEvent.get();

        String username = userDetails != null ? userDetails.getUsername() : null;

        boolean exists = waitlistEntryRepository.findByEventIdAndUsername(eventId, username)
                .isPresent();

        if (exists) {
            return ResponseEntity.badRequest().body("Already requested");
        }

        WaitlistEntry entry = WaitlistEntry.builder()
                .event(event)
                .username(username)
                .fullName(fullName)
                .status(WaitlistEntry.Status.PENDING)
                .build();

        waitlistEntryRepository.save(entry);
        return ResponseEntity.ok("Request submitted");
    }

    @PostMapping("/{eventId}/waitlist/{entryId}/status")
    public ResponseEntity<?> updateWaitlistStatus(
            @PathVariable Long eventId,
            @PathVariable Long entryId,
            @RequestParam WaitlistEntry.Status status,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Event> optEvent = eventRepository.findById(eventId);
        if (optEvent.isEmpty()) return ResponseEntity.notFound().build();

        Event event = optEvent.get();

        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Not authorized");
        }

        Optional<WaitlistEntry> optEntry = waitlistEntryRepository.findById(entryId);
        if (optEntry.isEmpty()) return ResponseEntity.notFound().build();

        WaitlistEntry entry = optEntry.get();
        entry.setStatus(status);
        waitlistEntryRepository.save(entry);

        return ResponseEntity.ok("Updated");
    }

    @GetMapping("/{eventId}/waitlist")
    public ResponseEntity<?> getWaitlist(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Optional<Event> optEvent = eventRepository.findById(eventId);
        if (optEvent.isEmpty()) return ResponseEntity.notFound().build();

        Event event = optEvent.get();

        if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body("Not authorized");
        }

        return ResponseEntity.ok(waitlistEntryRepository.findByEvent(event));
    }

    @PostMapping("upload-guestlist")
    public ResponseEntity<?> uploadGuestList(@RequestParam("file") MultipartFile file) {
        try {
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get("uploads/pdf/" + filename);
            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());

            String url = "/uploads/pdf/" + filename;
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Upload failed");
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
        User creator = userRepository.findById(userDetails.getId()).orElse(null);
        if (creator == null) return ResponseEntity.badRequest().body("User not found");

        Event event = new Event();
        event.setTitle(req.title);
        event.setDescription(req.description);
        event.setLocation(req.location);
        event.setDateTime(req.dateTime);
        event.setImageUrl(req.imageUrl);
        event.setCreatedBy(creator);
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
        event.setListOnly(req.listOnly);
        event.setGuestListPdf(req.guestListPdf);
        event.setType(req.type != null ? req.type : "event");

        if ("reunion".equalsIgnoreCase(event.getType())) {
            event.setBudgetGoal(req.budgetGoal);
            event.setBudgetPurpose(req.budgetPurpose);
        }

        eventRepository.save(event); // ✅ Save event first

        if ("reunion".equalsIgnoreCase(event.getType()) && req.guests != null) {
            Set<String> addedUsernames = new HashSet<>();

            for (ReunionGuestDTO g : req.guests) {
                if (!addedUsernames.add(g.username.toLowerCase())) {
                    // 👇 Skip duplicate usernames
                    continue;
                }

                Optional<User> userOpt = userRepository.findByUsername(g.username);
                if (userOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body("Username not found: " + g.username);
                }


                User guestUser = userOpt.get();
                ReunionGuest guest = new ReunionGuest();
                guest.setUsername(guestUser.getUsername());
                guest.setEmail(guestUser.getEmail());
                guest.setName(guestUser.getFirstName() + " " + guestUser.getLastName());
                guest.setPaid(g.paid);
                guest.setEvent(event);

                reunionGuestRepository.save(guest);
                event.getReunionGuests().add(guest);
            }
        }

        if (req.ticketTiers != null && !req.ticketTiers.isEmpty()) {
            int totalQty = req.ticketTiers.stream().mapToInt(t -> t.quantity).sum();
            double minPrice = req.ticketTiers.stream().mapToDouble(t -> t.price).min().orElse(0);
            event.setAvailableTickets(totalQty);
            event.setPrice(minPrice);
        }

        if (req.ticketTiers != null) {
            for (TicketTierRequest tierReq : req.ticketTiers) {
                TicketTier tier = new TicketTier();
                tier.setName(tierReq.name);
                tier.setPrice(tierReq.price);
                tier.setAvailableQuantity(tierReq.quantity);
                tier.setTierOrder(tierReq.order);
                tier.setStartTime(tierReq.startTime);
                tier.setEndTime(tierReq.endTime);
                tier.setForceOpen(tierReq.isForceOpen());
                tier.setEvent(event);
                ticketTierRepository.save(tier);
            }
        }

        return ResponseEntity.ok(Map.of(
                "id", event.getId(),
                "createdByUsername", creator.getUsername()
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

    // ... (keep other parts of EventController unchanged)

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id,
            @RequestBody EventRequest req) {

        return eventRepository.findById(id).map(event -> {
            if (!event.getCreatedBy().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Unauthorized");
            }

            // ✅ Only update fields that are present and different
            if (req.title != null && !req.title.equals(event.getTitle())) event.setTitle(req.title);
            if (req.description != null && !req.description.equals(event.getDescription())) event.setDescription(req.description);
            if (req.location != null && !req.location.equals(event.getLocation())) event.setLocation(req.location);
            if (req.dateTime != null && !req.dateTime.equals(event.getDateTime())) event.setDateTime(req.dateTime);
            if (req.imageUrl != null && !req.imageUrl.equals(event.getImageUrl())) event.setImageUrl(req.imageUrl);
            if (req.state != null && !req.state.equals(event.getState())) event.setState(req.state);
            if (req.address != null && !req.address.equals(event.getAddress())) event.setAddress(req.address);
            if (req.country != null && !req.country.equals(event.getCountry())) event.setCountry(req.country);
            if (req.latitude != null && !req.latitude.equals(event.getLatitude())) event.setLatitude(req.latitude);
            if (req.longitude != null && !req.longitude.equals(event.getLongitude())) event.setLongitude(req.longitude);
            if (req.zelleEmail != null && !req.zelleEmail.equals(event.getZelleEmail())) event.setZelleEmail(req.zelleEmail);
            if (req.zellePhone != null && !req.zellePhone.equals(event.getZellePhone())) event.setZellePhone(req.zellePhone);
            event.setAllowPagoMovil(req.allowPagoMovil);
            event.setAllowCash(req.allowCash);
            event.setListOnly(req.listOnly);
            event.setGuestListPdf(req.guestListPdf);
            if (req.pagoMovilPhone != null && !req.pagoMovilPhone.equals(event.getPagoMovilPhone())) event.setPagoMovilPhone(req.pagoMovilPhone);
            if (req.pagoMovilCi != null && !req.pagoMovilCi.equals(event.getPagoMovilCi())) event.setPagoMovilCi(req.pagoMovilCi);
            if (req.pagoMovilBank != null && !req.pagoMovilBank.equals(event.getPagoMovilBank())) event.setPagoMovilBank(req.pagoMovilBank);
            if (req.cashNote != null && !req.cashNote.equals(event.getCashNote())) event.setCashNote(req.cashNote);

            if ("reunion".equalsIgnoreCase(event.getType())) {
                if (req.budgetGoal != null && !req.budgetGoal.equals(event.getBudgetGoal())) {
                    event.setBudgetGoal(req.budgetGoal);
                }
                if (req.budgetPurpose != null && !req.budgetPurpose.equals(event.getBudgetPurpose())) {
                    event.setBudgetPurpose(req.budgetPurpose);
                }

                // ✅ Update reunion guests
                if (req.guests != null) {
                    Set<String> incomingUsernames = req.guests.stream()
                            .map(g -> g.username.toLowerCase())
                            .collect(Collectors.toSet());

                    // Remove guests from DB that are not in the new list
                    List<ReunionGuest> existingGuests = new ArrayList<>(event.getReunionGuests());
                    for (ReunionGuest guest : existingGuests) {
                        if (!incomingUsernames.contains(guest.getUsername().toLowerCase())) {
                            reunionGuestRepository.delete(guest);
                        }
                    }

                    // 🚨 Clear the list in the Event object to reflect the new state
                    event.getReunionGuests().clear();

                    Set<String> seen = new HashSet<>();
                    for (ReunionGuestDTO dto : req.guests) {
                        if (!seen.add(dto.username.toLowerCase())) continue;

                        Optional<User> userOpt = userRepository.findByUsername(dto.username);
                        if (userOpt.isEmpty()) continue;

                        User user = userOpt.get();

                        ReunionGuest guest = new ReunionGuest();
                        guest.setUsername(user.getUsername());
                        guest.setEmail(user.getEmail());
                        guest.setName(user.getFirstName() + " " + user.getLastName());
                        guest.setPaid(dto.paid);
                        guest.setEvent(event);

                        reunionGuestRepository.save(guest);
                        event.getReunionGuests().add(guest); // 💡 Rebuild the list fresh
                    }
                }
            }

            // ✅ Update ticket tiers only if present
            if (req.ticketTiers != null) {
                Set<TicketTier> existingTiers = new HashSet<>(event.getTicketTiers());
                Set<TicketTier> updatedTiers = new HashSet<>();

                for (TicketTierRequest tierReq : req.ticketTiers) {
                    TicketTier tier;

                    if (tierReq.id != null && tierReq.id != 0) {
                        tier = ticketTierRepository.findById(tierReq.id).orElse(null);
                        if (tier == null) continue;
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

                    updatedTiers.add(tier);
                }

                Set<Long> incomingIds = req.ticketTiers.stream()
                        .map(t -> t.id)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                Set<TicketTier> tiersToKeep = existingTiers.stream()
                        .filter(t -> incomingIds.contains(t.getId()))
                        .collect(Collectors.toSet());

                Set<TicketTier> tiersToDelete = existingTiers.stream()
                        .filter(t -> !incomingIds.contains(t.getId()))
                        .collect(Collectors.toSet());

                event.getTicketTiers().clear();
                event.getTicketTiers().addAll(tiersToKeep);
                event.getTicketTiers().addAll(updatedTiers);

                ticketTierRepository.saveAll(updatedTiers);
                ticketTierRepository.deleteAll(tiersToDelete);
            }

            eventRepository.save(event);
            return ResponseEntity.ok(event);

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

        public boolean listOnly = false;
        public String guestListPdf;

    }

    @Getter
    @Setter
    static class ReunionGuestDTO {
        public String username; // 👈 Required
        public boolean paid;
    }


}
