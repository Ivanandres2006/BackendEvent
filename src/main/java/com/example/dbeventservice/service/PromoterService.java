package com.example.dbeventservice.service;

import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PromoterService {

    @Autowired
    private PromoterRepository promoterRepository;

    @Autowired
    private PromoterEventRepository promoterEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    public Promoter createPromoter(User user) {
        Promoter promoter = new Promoter();
        promoter.setUser(user);
        String referralCode = user.getUsername().toLowerCase();
        promoter.setReferralCode(referralCode);
        return promoterRepository.save(promoter);
    }

    public Optional<Promoter> getPromoterByUser(User user) {
        return promoterRepository.findByUser(user);
    }

    public Optional<PromoterEvent> getPromoterEvent(Long promoterId, Long eventId) {
        return promoterEventRepository.findByPromoterIdAndEventId(promoterId, eventId);
    }


    public List<PromoterEvent> getEventsByPromoter(Promoter promoter) {
        return promoterEventRepository.findByPromoter(promoter);
    }

    public PromoterEvent assignEventToPromoter(Long eventId, Promoter promoter) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        return promoterEventRepository.findByPromoterAndEvent(promoter, event)
                .orElseGet(() -> {
                    PromoterEvent pe = new PromoterEvent();
                    pe.setPromoter(promoter);
                    pe.setEvent(event);
                    pe.setTicketsSold(0);
                    return promoterEventRepository.save(pe);
                });
    }

    public List<PromoterEvent> getPromotersForEvent(Event event) {
        return promoterEventRepository.findByEvent(event);
    }
}
