package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.repositories.TicketRepository;
import com.example.dbeventservice.service.PassService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/passes")
public class PassController {

    private final PassService passService;
    private final TicketRepository ticketRepository;

    public PassController(PassService passService, TicketRepository ticketRepository) {
        this.passService = passService;
        this.ticketRepository = ticketRepository;
    }

    // Returns a signed .pkpass for THIS user's ticket
    @GetMapping(value = "/ticket/{ticketId}", produces = "application/vnd.apple.pkpass")
    public ResponseEntity<byte[]> getTicketPass(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetailsImpl me
    ) throws Exception {

        if (me == null) return ResponseEntity.status(401).build();
        Ticket t = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        // Security: only owner of the ticket OR event owner can fetch
        Long myId = me.getId();
        boolean isOwner = t.getOwner() != null && t.getOwner().getId().equals(myId);
        Event e = t.getEvent();
        boolean isEventCreator = (e != null && e.getCreatedBy() != null && e.getCreatedBy().getId().equals(myId));
        if (!isOwner && !isEventCreator) {
            return ResponseEntity.status(403).build();
        }

        // Build data
        String title = e != null ? e.getTitle() : "WKND Event";
        LocalDateTime start = e != null ? e.getDateTime() : LocalDateTime.now().plusDays(1);
        String location = (e != null ? e.getLocation() : "TBD")
                + (e != null && e.getState() != null ? ", " + e.getState() : "");
        String ownerEmail = (t.getOwner() != null && t.getOwner().getEmail() != null)
                ? t.getOwner().getEmail() : "unknown@wknd.app";

        // Use the SAME QR payload pattern you already use everywhere
        //  "TICKET:<ticketId>:<email>"
        String qrPayload = "TICKET:" + t.getId() + ":" + ownerEmail;

        byte[] pkpass = passService.buildEventPass(t.getId(), title, start, location, qrPayload);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=ticket-" + t.getId() + ".pkpass")
                .header("Cache-Control", "no-store")
                .body(pkpass);
    }
}
