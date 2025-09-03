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

import static com.example.dbeventservice.model.Enum.PaymentMethod.zelle;

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



        // configurable grace (default 6 hours)
        @org.springframework.beans.factory.annotation.Value("${app.event.futureGraceHours:6}")
        private long futureGraceHours;

        private LocalDateTime nowMinusGrace() {
            return LocalDateTime.now().minusHours(futureGraceHours);
        }

        @Transactional
        public ResalePost createResalePost(
                Long ticketId, double price, String title, String description, Long userId,
                String zelle, String pmPhone, String pmCi, String pmBank, String cashNote) {
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if (ticket.getOwner() == null || !ticket.getOwner().getId().equals(userId)) {
                throw new IllegalArgumentException("You don't own this ticket.");
            }
            if (!ticket.isPaid()) throw new IllegalArgumentException("Only paid tickets can be listed.");
            if (ticket.isUsed())  throw new IllegalArgumentException("Used tickets cannot be listed.");
            if (resalePostRepository.existsByTicketAndSoldFalse(ticket)) {
                throw new IllegalArgumentException("This ticket is already listed for resale.");
            }

            // ✅ TIME CHECK BEFORE SAVE, with grace
            var event = ticket.getEvent();
            var dt = (event != null) ? event.getDateTime() : null;
            if (dt != null && !dt.isAfter(LocalDateTime.now())) {     // ⬅️ strict
                throw new IllegalArgumentException("Event has already passed; cannot list for resale.");
            }

            // proceed to create
            ResalePost saved = resalePostRepository.save(
                    ResalePost.builder()
                            .ticket(ticket)
                            .seller(user)
                            .price(price)
                            .title(title)
                            .description(description)
                            .sellerZelleEmail(zelle)
                            .sellerPagoMovilPhone(pmPhone)
                            .sellerPagoMovilCi(pmCi)
                            .sellerPagoMovilBank(pmBank)
                            .sellerCashNote(cashNote)
                            .createdAt(LocalDateTime.now())
                            .sold(false)
                            .build()
            );

            // optional notify
            if (event != null) {
                notificationService.notifyHotEventResaleAdded(event.getId(), saved.getId(), userId);
            }
            return saved;
        }

        public List<ResalePost> getAllAvailablePosts() {
            try {
                return resalePostRepository.findAvailableWithFutureEvent(LocalDateTime.now()); // ⬅️ strict
            } catch (Exception ignored) {
                return resalePostRepository.findBySoldFalseAndPausedFalseOrderByCreatedAtDesc()
                        .stream()
                        .filter(this::eventIsInFuture)   // ⬅️ strict helper below
                        .toList();
            }
        }

        private boolean eventIsInFutureWithGrace(ResalePost p) {
            var e = (p.getTicket() != null) ? p.getTicket().getEvent() : null;
            var when = (e != null) ? e.getDateTime() : null;
            return when != null && when.isAfter(nowMinusGrace());
        }

        private boolean eventIsInFuture(ResalePost p) {
            var e = (p.getTicket() != null) ? p.getTicket().getEvent() : null;
            var when = (e != null) ? e.getDateTime() : null;
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
