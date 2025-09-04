package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.ReferredBuyerDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PromoterService {

    @Autowired private PromoterRepository promoterRepository;
    @Autowired private PromoterEventRepository promoterEventRepository;
    @Autowired private PromoterSaleRepository promoterSaleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ChatService chatService; // keep your group-refresh hook
    @Autowired private NotificationService notificationService;


    @Transactional
    public Promoter ensurePromoter(User user) {
        return promoterRepository.findByUser(user).orElseGet(() -> {
            String base = user.getUsername() == null ? ("u" + user.getId()) : user.getUsername().toLowerCase();
            String code = base.replaceAll("[^a-z0-9]", "");
            if (code.length() < 3) code = code + user.getId();
            String unique = code;
            int i = 1;
            while (promoterRepository.existsByReferralCode(unique)) {
                unique = code + i++;
            }
            Promoter p = new Promoter();
            p.setUser(user);
            p.setReferralCode(unique);
            p.setActive(true);
            p.setCreatedAt(LocalDateTime.now());
            return promoterRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public Optional<Promoter> getPromoterByUser(User user) {
        return promoterRepository.findByUser(user);
    }

    @Transactional
    public PromoterEvent assignEventToPromoter(Long eventId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        Promoter promoter = ensurePromoter(user);

        PromoterEvent pe = promoterEventRepository.findByPromoterAndEvent(promoter, event)
                .orElseGet(() -> {
                    PromoterEvent created = new PromoterEvent();
                    created.setPromoter(promoter);
                    created.setEvent(event);
                    created.setTicketsSold(0);
                    created.setGrossRevenue(BigDecimal.ZERO);
                    created.setCommissionEarned(BigDecimal.ZERO);
                    created.setCommissionEnabled(false);
                    created.setCommissionActivatedAt(null);
                    return promoterEventRepository.save(created);
                });

        // idempotent chat refresh
        chatService.refreshPromoterGroupParticipants(eventId);

        notificationService.notifyPromoterAssigned(eventId, user.getId());


        return pe;
    }

    /**
     * Owner toggles commission and/or updates rate.
     * @param eventId     Event id
     * @param username    Promoter username
     * @param enabled     Optional: set commission enabled/disabled
     * @param rate        Optional: new commission rate (0.00..1.00)
     * @param retroactive If enabling and true, recompute including past sales; otherwise only future sales accrue
     */
    @Transactional
    public PromoterEvent updateCommission(Long eventId, String username, Boolean enabled, BigDecimal rate, boolean retroactive) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        Promoter promoter = ensurePromoter(user);
        PromoterEvent pe = promoterEventRepository.findByPromoterAndEvent(promoter, event)
                .orElseThrow(() -> new IllegalArgumentException("Promoter is not assigned to this event"));

        if (rate != null) {
            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Commission rate must be between 0.00 and 1.00");
            }
            pe.setCommissionRate(rate);
        }

        if (enabled != null) {
            if (enabled && !pe.isCommissionEnabled()) {
                // Turning ON
                pe.setCommissionEnabled(true);
                pe.setCommissionActivatedAt(LocalDateTime.now());
                if (retroactive) {
                    // Include all past sales
                    pe.setCommissionEarned(sumCommissionFor(pe, null));
                } else {
                    // Only future sales will accrue
                    pe.setCommissionEarned(pe.getCommissionEarned()); // leave as-is (likely 0)
                }
            } else if (!enabled && pe.isCommissionEnabled()) {
                // Turning OFF
                pe.setCommissionEnabled(false);
                pe.setCommissionActivatedAt(null);
                // Business rule: disable -> zero out earned (so it doesn't “exist” until re-enabled)
                pe.setCommissionEarned(BigDecimal.ZERO);
            }
        }

        return promoterEventRepository.save(pe);
    }

    private BigDecimal sumCommissionFor(PromoterEvent pe, LocalDateTime from) {
        BigDecimal sum = BigDecimal.ZERO;
        List<PromoterSale> sales = promoterSaleRepository.findByPromoterEvent(pe);
        for (PromoterSale s : sales) {
            if (from == null || (s.getTimestamp() != null && !s.getTimestamp().isBefore(from))) {
                sum = sum.add(s.getTotalPrice().multiply(pe.getCommissionRate()));
            }
        }
        return sum;
    }

    /**
     * Called on each sale. Only increments commission if commissionEnabled == true
     * (and, implicitly, will count only sales after activation time).
     */
    @Transactional
    public void recordSale(Long eventId, Long buyerId, int quantity, BigDecimal unitPrice, String referralCode) {
        if (referralCode == null || referralCode.isBlank()) return;

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        Promoter promoter = promoterRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));

        PromoterEvent pe = promoterEventRepository.findByPromoterAndEvent(promoter, event)
                .orElseGet(() -> {
                    PromoterEvent created = new PromoterEvent();
                    created.setPromoter(promoter);
                    created.setEvent(event);
                    created.setTicketsSold(0);
                    created.setGrossRevenue(BigDecimal.ZERO);
                    created.setCommissionEarned(BigDecimal.ZERO);
                    created.setCommissionEnabled(false);
                    created.setCommissionActivatedAt(null);
                    return promoterEventRepository.save(created);
                });

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        // Create sale
        PromoterSale sale = new PromoterSale();
        sale.setPromoterEvent(pe);
        sale.setBuyer(buyer);
        sale.setTimestamp(LocalDateTime.now());
        int qty = Math.max(1, quantity);
        sale.setQuantity(qty);
        BigDecimal unit = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        sale.setUnitPrice(unit);
        sale.setTotalPrice(unit.multiply(BigDecimal.valueOf(qty)));
        promoterSaleRepository.save(sale);

        // Aggregates always count tickets & revenue
        pe.setTicketsSold(pe.getTicketsSold() + qty);
        pe.setGrossRevenue(pe.getGrossRevenue().add(sale.getTotalPrice()));

        // 💡 Commission only accrues if enabled at the time of sale
        if (pe.isCommissionEnabled()) {
            if (pe.getCommissionActivatedAt() == null ||
                    !sale.getTimestamp().isBefore(pe.getCommissionActivatedAt())) {
                pe.setCommissionEarned(
                        pe.getCommissionEarned().add(sale.getTotalPrice().multiply(pe.getCommissionRate()))
                );
            }
        }
        promoterEventRepository.save(pe);
    }

    @Transactional(readOnly = true)
    public List<PromoterEvent> getPromotersForEvent(Event event) {
        return promoterEventRepository.findByEvent(event);
    }

    @Transactional(readOnly = true)
    public List<ReferredBuyerDTO> getReferredBuyers(Long promoterId, Long eventId) {
        Promoter promoter = promoterRepository.findById(promoterId).orElseThrow();
        Event event = eventRepository.findById(eventId).orElseThrow();

        PromoterEvent pe = promoterEventRepository.findByPromoterAndEvent(promoter, event)
                .orElseThrow();

        List<PromoterSale> sales = promoterSaleRepository.findByPromoterEvent(pe);
        List<ReferredBuyerDTO> list = new ArrayList<>();
        for (PromoterSale s : sales) {
            list.add(new ReferredBuyerDTO(
                    s.getBuyer().getId(),
                    s.getBuyer().getUsername(),
                    s.getBuyer().getEmail(),
                    s.getTimestamp()
            ));
        }
        return list;
    }
}
