package com.example.dbeventservice.service;

import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.ResalePostRepository;
import com.example.dbeventservice.repositories.TicketRepository;
import com.example.dbeventservice.repositories.UserRepository;
import org.springframework.transaction.annotation.Transactional; // 👈 NEW
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.repositories.ResalePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResalePostService {

    @Autowired
    private ResalePostRepository resalePostRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService; // 👈 NEW

    @Transactional // 👈 NEW
    public ResalePost createResalePost(Long ticketId, double price, String title, String description, Long userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (ticket.getOwner() == null || !ticket.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("You don't own this ticket.");
        }
        if (!ticket.isPaid())             throw new IllegalArgumentException("Only paid tickets can be listed.");
        if (ticket.isUsed())              throw new IllegalArgumentException("Used tickets cannot be listed.");
        if (resalePostRepository.existsByTicketAndSoldFalse(ticket)) {
            throw new IllegalArgumentException("This ticket is already listed for resale.");
        }

        ResalePost post = ResalePost.builder()
                .ticket(ticket)
                .seller(user)
                .price(price)
                .title(title)
                .createdAt(LocalDateTime.now())
                .description(description)
                .sold(false)
                .build();

        ResalePost saved = resalePostRepository.save(post);

        if (ticket.getEvent() != null && ticket.getEvent().getDateTime() != null) {
            if (!ticket.getEvent().getDateTime().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Event has already passed; cannot list for resale.");
            }
        }

        // 🔥 Notify audience if the event is hot (method itself checks “hot” again)
        notificationService.notifyHotEventResaleAdded(
                ticket.getEvent().getId(), saved.getId(), userId
        ); // -> data { type:"SOCIAL", kind:"HOT_EVENT_RESALE_ADDED", eventId, resaleId }

        return saved;
    }



    public List<ResalePost> getAllAvailablePosts() {
        try {
            return resalePostRepository.findAvailableWithFutureEvent(LocalDateTime.now());
        } catch (Exception ignored) {
            return resalePostRepository.findBySoldFalseAndPausedFalseOrderByCreatedAtDesc()
                    .stream()
                    .filter(this::eventIsInFuture)   // uses the helper above
                    .toList();
        }
    }

    private boolean eventIsInFuture(ResalePost p) {
        var t = p.getTicket();
        if (t == null) return false;

        var e = t.getEvent();
        if (e == null) return false;

        LocalDateTime when = e.getDateTime();   // <-- use Event.dateTime
        return when != null && when.isAfter(LocalDateTime.now());
    }

    public List<ResalePost> getUserPosts(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return resalePostRepository.findBySeller(user);
    }

    public Optional<ResalePost> getPostById(Long postId) {
        return resalePostRepository.findById(postId);
    }

    @Transactional // 👈 NEW
    public void markAsSold(Long postId) {
        resalePostRepository.findById(postId).ifPresent(post -> {
            post.setSold(true);
            resalePostRepository.save(post);

            notificationService.notifyResaleSold(postId);
        });
    }
}
