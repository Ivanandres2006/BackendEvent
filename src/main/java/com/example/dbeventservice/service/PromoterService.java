package com.example.dbeventservice.service;

import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private ChatService chatService;


    @Transactional
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

    @Transactional
    public PromoterEvent assignEventToPromoter(Long eventId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // ✅ Ensure the promoter exists
        Promoter promoter = promoterRepository.findByUser(user)
                .orElseGet(() -> {
                    Promoter newPromoter = new Promoter();
                    newPromoter.setUser(user);
                    newPromoter.setReferralCode(user.getUsername().toLowerCase());
                    return promoterRepository.save(newPromoter);
                });

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        System.out.println("🔁 Assigning promoter: " + user.getUsername());

        System.out.println("🔁 Assigning promoter: " +
                (promoter.getUser() != null ? promoter.getUser().getUsername() : "null user"));

        PromoterEvent pe = promoterEventRepository.findByPromoterAndEvent(promoter, event)
                .orElseGet(() -> {
                    PromoterEvent newPe = new PromoterEvent();
                    newPe.setPromoter(promoter);
                    newPe.setEvent(event);
                    newPe.setTicketsSold(0);
                    return promoterEventRepository.save(newPe);
                });

        // ✅ Refresh participants in promoter group chat
        chatService.refreshPromoterGroupParticipants(eventId);

        return pe;
    }


    public List<PromoterEvent> getPromotersForEvent(Event event) {
        return promoterEventRepository.findByEvent(event);
    }
}
