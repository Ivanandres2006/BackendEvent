// ✅ UPDATED TicketService.java with ALL fixes
package com.example.dbeventservice.service;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.dbeventservice.dto.FeeBreakdown;
import com.example.dbeventservice.model.Enum.PaymentMethod;
import com.example.dbeventservice.service.FeeCalculator;

import com.example.dbeventservice.model.*;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.*;
import com.example.dbeventservice.util.QRCodeGenerator;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketTierRepository ticketTierRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PromoterRepository promoterRepository;

    @Autowired
    private PromoterService promoterService;

    @Autowired
    private PromoterEventRepository promoterEventRepository;

    @Autowired
    private PromoterSaleRepository promoterSaleRepository;

    @Autowired
    private ReunionGuestRepository reunionGuestRepository;

    @Autowired
    private FeeCalculator feeCalculator;

    @Autowired
    private EmailService emailService;


    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);


    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    public TicketService(
            TicketRepository ticketRepository,
            TicketTierRepository ticketTierRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            FeeCalculator feeCalculator
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketTierRepository = ticketTierRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.feeCalculator = feeCalculator;
    }


    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public Map<String, Object> createCheckout(
            Long userId,
            Long eventId,
            Long ticketTierId,
            int quantity,
            String email,
            String ref,
            String paymentMethod
    ) throws StripeException {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<TicketTier> tierOpt = ticketTierRepository.findById(ticketTierId);
        Optional<Event> eventOpt = eventRepository.findById(eventId);

        if (userOpt.isEmpty() || tierOpt.isEmpty() || eventOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid user, event, or tier ID");
        }

        TicketTier tier = tierOpt.get();
        Event event = eventOpt.get();
        User user = userOpt.get();

        if (tier.getAvailableQuantity() < quantity) {
            throw new IllegalStateException("Not enough tickets available");
        }

        Map<String, Object> response = new HashMap<>();

        // ✅ Handle free tickets and Zelle
        boolean isFree = tier.getPrice() <= 0.0;
        boolean isManualPayment = paymentMethod != null &&
                (paymentMethod.equalsIgnoreCase("zelle") ||
                        paymentMethod.equalsIgnoreCase("pagoMovil") ||
                        paymentMethod.equalsIgnoreCase("cash"));

        if (isFree || isManualPayment) {
            List<Ticket> tickets = new ArrayList<>();
            for (int i = 0; i < quantity; i++) {
                Ticket ticket = new Ticket();
                ticket.setOwner(user);
                ticket.setEvent(event);
                ticket.setTicketTier(tier);
                ticket.setPaymentMethod(paymentMethod != null ? paymentMethod : "free");
                ticket.setQuantity(1);
                if (isFree) {
                    ticket.setPaid(true);
                    ticket.setStatus(TicketStatus.PAID);
                } else {
                    ticket.setPaid(false);
                    ticket.setStatus(TicketStatus.PENDING);
                }
                ticketRepository.save(ticket);

                ticket.setQrCodeUrl("/api/tickets/qr/" + ticket.getId());
                ticketRepository.save(ticket);

                tickets.add(ticket);
            }

            if (!event.getAttendees().contains(user)) {
                event.getAttendees().add(user);
                eventRepository.save(event);
            }

            tier.setAvailableQuantity(Math.max(tier.getAvailableQuantity() - quantity, 0));
            ticketTierRepository.save(tier);
            event.updateAggregateInfoFromTiers();
            eventRepository.save(event);

            if (ref != null && !ref.isBlank()) {
                promoterRepository.findByReferralCode(ref).ifPresent(promoter -> {
                    PromoterEvent pe = promoterService.assignEventToPromoter(eventId, promoter.getUser().getUsername());
                    pe.setTicketsSold(pe.getTicketsSold() + 1);
                    promoterEventRepository.save(pe);

                    PromoterSale sale = new PromoterSale();
                    sale.setPromoterEvent(pe);
                    sale.setBuyer(user);
                    sale.setTimestamp(LocalDateTime.now());
                    promoterSaleRepository.save(sale);
                });
            }

            if (isFree) {
                firePurchaseNotifs(event, user);
            }

            // ✅ ADD THIS (compute and include the same breakdown for UI)
            com.example.dbeventservice.model.Enum.PaymentMethod _method =
                    com.example.dbeventservice.model.Enum.PaymentMethod.from(paymentMethod);
            long _unitPriceCents = Math.round((tier.getPrice() == null ? 0.0 : tier.getPrice()) * 100.0);
            FeeBreakdown _fee = feeCalculator.quote(_unitPriceCents, quantity, _method);
            response.put("breakdown", _fee);

            response.put("manual", isManualPayment);
            response.put("free", isFree);
            return response;
        }

        // ✅ Stripe Payment (card)
        // ✅ Compute unified service fee; Stripe fee folded in for card
        PaymentMethod _method = PaymentMethod.from(paymentMethod);
        long _unitPriceCents = Math.round((tier.getPrice() == null ? 0.0 : tier.getPrice()) * 100.0);
        FeeBreakdown _fee = feeCalculator.quote(_unitPriceCents, quantity, _method);

        // Return the breakdown so the app can show it post-checkout too
        response.put("breakdown", _fee);

        // Stripe amount must be the TOTAL (subtotal + service fee)
        long amount = _fee.getTotalCents();

        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency("usd")
                .setReceiptEmail(email)
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("eventId", String.valueOf(eventId))
                .putMetadata("ticketTierId", String.valueOf(ticketTierId))
                .putMetadata("quantity", String.valueOf(quantity))
                .putMetadata("email", email)
                .putMetadata("serviceFeeCents", String.valueOf(_fee.getServiceFeeCents()))
                .putMetadata("platformFeeCents", String.valueOf(_fee.getPlatformFeeCents()))
                .putMetadata("stripeFeeCents", String.valueOf(_fee.getStripeFeeCents()))
                .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                );

        if (ref != null && !ref.isBlank()) {
            builder.putMetadata("ref", ref);
        }

        PaymentIntent intent = PaymentIntent.create(builder.build());
        System.out.println("✅ Created PaymentIntent: " + intent.getId());

        response.put("clientSecret", intent.getClientSecret());
        return response;
    }

    private void firePurchaseNotifs(Event event, User buyer) {
        try {
            // 1) Owner: “New ticket sold”
            notificationService.notifyOwnerTicketPurchased(event.getId(), buyer.getId());

            // 2) Owner milestones: 50% / 80% / sold out (sent once each)
            notificationService.checkAndNotifyMilestones(event.getId());

            // 3) Followers of buyer: “Ivan is going…”
            notificationService.notifyFriendsGoing(buyer.getId(), event.getId());
        } catch (Exception e) {
            logger.warn("⚠️ Failed to send purchase notifications", e);
        }
    }


    public Map<String, Object> createReunionCheckout(
            Long userId,
            Long eventId,
            String email
    ) throws StripeException {

        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Event> eventOpt = eventRepository.findById(eventId);

        if (userOpt.isEmpty() || eventOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid user or event ID");
        }

        User user = userOpt.get();
        Event event = eventOpt.get();

        List<ReunionGuest> guests = reunionGuestRepository.findByEventId(eventId);
        int totalGuests = guests.size() + 1;
        double perGuestAmount = event.getBudgetGoal() / totalGuests;
        long amount = (long) (perGuestAmount * 100);

        // ✅ Create PaymentIntent
        PaymentIntentCreateParams intentParams = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency("usd")
                .setReceiptEmail(email)
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("username", user.getUsername()) // ✅ ADD THIS
                .putMetadata("eventId", String.valueOf(eventId))
                .putMetadata("type", "reunion")
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        PaymentIntent intent = PaymentIntent.create(intentParams);

        Map<String, Object> response = new HashMap<>();
        response.put("clientSecret", intent.getClientSecret());
        return response;
    }

    @Transactional
    public void confirmReunionPayment(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            Map<String, String> metadata = intent.getMetadata();

            String usernameRaw = metadata.get("username");
            String eventIdStr = metadata.get("eventId");

            if (usernameRaw == null || eventIdStr == null) return;

            String usernameNormalized = usernameRaw.trim().toLowerCase();
            Long eventId = Long.parseLong(eventIdStr);

            // Try normal repo method
            Optional<ReunionGuest> guestOpt = reunionGuestRepository.findByUsernameIgnoreCaseAndEventId(usernameNormalized, eventId);

            if (guestOpt.isPresent()) {
                ReunionGuest guest = guestOpt.get();
                guest.setPaid(true);
                guest.setAttending(true);
                reunionGuestRepository.saveAndFlush(guest);
                return;
            }

            // 🔁 Fallback: loop through guests and match manually
            List<ReunionGuest> allGuests = reunionGuestRepository.findByEventId(eventId);
            for (ReunionGuest guest : allGuests) {
                if (guest.getUsername() != null && guest.getUsername().trim().toLowerCase().equals(usernameNormalized)) {
                    guest.setPaid(true);
                    guest.setAttending(true);
                    reunionGuestRepository.save(guest);
                    reunionGuestRepository.flush(); // ✅ Ensure it flushes to DB
                    return;
                }
            }

        } catch (Exception e) {
            e.printStackTrace(); // ✅ Make sure logs go to stderr
        }
    }



    public void confirmPayment(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

            if ("succeeded".equals(intent.getStatus())) {
                Map<String, String> metadata = intent.getMetadata();

                Long userId = Long.parseLong(metadata.get("userId"));
                Long eventId = Long.parseLong(metadata.get("eventId"));
                Long ticketTierId = Long.parseLong(metadata.get("ticketTierId"));
                int quantity = Integer.parseInt(metadata.get("quantity"));
                String email = metadata.get("email");

                Optional<User> userOpt = userRepository.findById(userId);
                Optional<Event> eventOpt = eventRepository.findById(eventId);
                Optional<TicketTier> tierOpt = ticketTierRepository.findById(ticketTierId);

                if (userOpt.isEmpty() || eventOpt.isEmpty() || tierOpt.isEmpty()) return;

                User user = userOpt.get();
                Event event = eventOpt.get();
                TicketTier tier = tierOpt.get();

                if (tier.getAvailableQuantity() < quantity) {
                    System.out.println("❌ Not enough tickets left at confirmation time.");
                    return;
                }

                List<Ticket> tickets = new ArrayList<>();
                for (int i = 0; i < quantity; i++) {
                    Ticket ticket = new Ticket();
                    ticket.setOwner(user);
                    ticket.setEvent(event);
                    ticket.setTicketTier(tier);
                    ticket.setPaymentMethod("stripe");
                    ticket.setPaid(true);
                    ticket.setQuantity(1);
                    ticket.setStatus(TicketStatus.PAID);
                    ticketRepository.save(ticket);

                    ticket.setQrCodeUrl("/api/tickets/qr/" + ticket.getId());
                    emailService.sendTicketEmail(user.getEmail(), user.getFirstName(), event, tickets);
                    ticketRepository.save(ticket);


                    tickets.add(ticket);
                }


                if (!event.getAttendees().contains(user)) {
                    event.getAttendees().add(user);
                    eventRepository.save(event);
                    firePurchaseNotifs(event, user);
                }

                tier.setAvailableQuantity(Math.max(tier.getAvailableQuantity() - quantity, 0));
                ticketTierRepository.save(tier);
                event.updateAggregateInfoFromTiers();
                eventRepository.save(event);

                String ref = metadata.get("ref");
                if (ref != null && !ref.isBlank()) {
                    promoterRepository.findByReferralCode(ref).ifPresent(promoter -> {
                        PromoterEvent pe = promoterService.assignEventToPromoter(eventId, promoter.getUser().getUsername());
                        pe.setTicketsSold(pe.getTicketsSold() + 1);
                        promoterEventRepository.save(pe);

                        PromoterSale sale = new PromoterSale();
                        sale.setPromoterEvent(pe);
                        sale.setBuyer(user);
                        sale.setTimestamp(LocalDateTime.now());
                        promoterSaleRepository.save(sale);
                    });
                }
            }

        } catch (Exception e) {
            System.out.println("❌ Stripe confirm error: " + e.getMessage());
        }
    }

    public List<Ticket> getUserTickets(Long userId) {
        return userRepository.findById(userId)
                .map(ticketRepository::findByOwner)
                .orElse(Collections.emptyList());
    }
}
