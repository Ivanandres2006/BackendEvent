package com.example.dbeventservice.service;

public interface EligibilityService {
    /** Return true if user owns a valid ticket to eventId and the event is upcoming (>= now). */
    boolean hasUpcomingTicket(Long userId, Long eventId);
}
