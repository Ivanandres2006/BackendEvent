// ✅ UPDATED TicketService.java with ALL fixes
package com.example.dbeventservice.service;

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
    private PromoterRepository promoterRepository;

    @Autowired
    private PromoterService promoterService;

    @Autowired
    private PromoterEventRepository promoterEventRepository;

    @Autowired
    private PromoterSaleRepository promoterSaleRepository;

    @Autowired
    private ReunionGuestRepository reunionGuestRepository;


    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    public TicketService(
            TicketRepository ticketRepository,
            TicketTierRepository ticketTierRepository,
            EventRepository eventRepository,
            UserRepository userRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketTierRepository = ticketTierRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
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
            Ticket ticket = new Ticket();
            ticket.setOwner(user);
            ticket.setEvent(event);
            ticket.setTicketTier(tier);
            ticket.setPaymentMethod(paymentMethod != null ? paymentMethod : "free");
            ticket.setQuantity(quantity);

            if (isFree) {
                ticket.setPaid(true);
                ticket.setStatus(TicketStatus.PAID);
            } else {
                ticket.setPaid(false); // ❗ not paid yet
                ticket.setStatus(TicketStatus.PENDING); // ❗ organizer decides
            }

            ticketRepository.save(ticket);

            ticket.setQrCodeUrl(QRCodeGenerator.generate(ticket.getId(), email));
            ticketRepository.save(ticket);

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

            response.put("manual", isManualPayment);
            response.put("free", isFree);
            return response;
        }

        // ✅ Stripe Payment (card)
        long amount = (long) (tier.getPrice() * quantity * 100);

        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency("usd")
                .setReceiptEmail(email)
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("eventId", String.valueOf(eventId))
                .putMetadata("ticketTierId", String.valueOf(ticketTierId))
                .putMetadata("quantity", String.valueOf(quantity))
                .putMetadata("email", email)
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

    public void confirmReunionPayment(String paymentIntentId) {
        try {
            System.out.println("📨 Starting confirmReunionPayment for intentId=" + paymentIntentId);

            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            Map<String, String> metadata = intent.getMetadata();

            System.out.println("🧾 Metadata: " + metadata);

            String userIdStr = metadata.get("userId");
            String eventIdStr = metadata.get("eventId");
            String username = metadata.get("username");

            if (userIdStr == null || eventIdStr == null || username == null) {
                System.out.println("⚠️ Reunion payment missing metadata. userId=" + userIdStr + ", eventId=" + eventIdStr + ", username=" + username);
                return;
            }

            Long eventId = Long.parseLong(eventIdStr);

            System.out.println("🔎 Looking for ReunionGuest username=" + username + ", eventId=" + eventId);

            List<ReunionGuest> guests = reunionGuestRepository.findByEventId(eventId);
            System.out.println("👥 All guests in event: " + guests.stream().map(ReunionGuest::getUsername).toList());

            Optional<ReunionGuest> guestOpt = reunionGuestRepository.findByUsernameAndEventId(username, eventId);
            if (guestOpt.isPresent()) {
                ReunionGuest guest = guestOpt.get();
                guest.setPaid(true);
                guest.setAttending(true);
                reunionGuestRepository.save(guest);

                System.out.println("✅ Reunion payment saved for: " + username);
            } else {
                System.out.println("❌ ReunionGuest not found for username=" + username + ", eventId=" + eventId);
            }

        } catch (Exception e) {
            System.out.println("❌ confirmReunionPayment error: " + e.getMessage());
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

                Ticket ticket = new Ticket();
                ticket.setOwner(user);
                ticket.setEvent(event);
                ticket.setTicketTier(tier);
                ticket.setPaymentMethod("stripe");
                ticket.setPaid(true); // only set if payment is confirmed
                ticket.setQuantity(quantity);
                ticket.setStatus(TicketStatus.PAID);
                ticketRepository.save(ticket);

                ticket.setQrCodeUrl(QRCodeGenerator.generate(ticket.getId(), email));
                ticketRepository.save(ticket);

                if (!event.getAttendees().contains(user)) {
                    event.getAttendees().add(user);
                    eventRepository.save(event);
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
