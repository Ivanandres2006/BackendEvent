package com.example.dbeventservice.service;

import com.example.dbeventservice.repositories.EventRepository;
import com.example.dbeventservice.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Gate logic for photo uploads:
 * 1) Event must be within [event.dateTime - graceBefore, event.dateTime + graceAfter]
 * 2) User must have a PAID, non-refunded ticket for that event
 */
@Service
@Primary
@RequiredArgsConstructor
public class EligibilityServiceDefault implements EligibilityService {

    private final TicketRepository tickets;
    private final EventRepository events;

    /** Allow posting up to N hours BEFORE the event (0 = no early window). */
    @Value("${photos.gate.graceHoursBefore:0}")
    private long graceHoursBefore;

    /** Allow posting up to N hours AFTER the event (0 = strictly before/at start). */
    @Value("${photos.gate.graceHoursAfter:0}")
    private long graceHoursAfter;

    /** Timezone to interpret Event.dateTime (your Event uses LocalDateTime). */
    @Value("${app.timezone:America/Chicago}")
    private String appTimezone;

    @Override
    public boolean hasUpcomingTicket(Long userId, Long eventId) {
        var opt = events.findById(eventId);
        if (opt.isEmpty()) return false;

        var event = opt.get();
        LocalDateTime eventAt = event.getDateTime(); // <-- your Event has LocalDateTime dateTime
        if (eventAt == null) return false;

        var zoneNow = LocalDateTime.now(ZoneId.of(appTimezone));
        var earliest = eventAt.minusHours(Math.max(0, graceHoursBefore));
        var latest   = eventAt.plusHours(Math.max(0, graceHoursAfter));

        boolean withinWindow = !zoneNow.isBefore(earliest) && !zoneNow.isAfter(latest);
        if (!withinWindow) return false;

        // Ownership / payment check in TicketRepository (see below)
        return tickets.userHasPaidTicketForEvent(userId, eventId);
    }
}
