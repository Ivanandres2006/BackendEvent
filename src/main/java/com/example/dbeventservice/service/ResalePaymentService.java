package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.PaymentIntentResponse;
import com.example.dbeventservice.dto.PaymentOptionsDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.model.Enum.PaymentMethod;
import com.example.dbeventservice.model.Enum.ResaleOrderStatus;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.EphemeralKey;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ResalePaymentService {

    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    @Autowired private ResalePostRepository resalePostRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ResaleOrderRepository resaleOrderRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private ResalePostRepository postRepo;
    @Autowired private EmailService emailService;
    @Autowired private ChatMessageRepository chatMessageRepository;

    @Autowired private ChatService chatService;


    // Be tolerant of old/new shapes & casing
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);


    @PostConstruct
    public void init() { Stripe.apiKey = stripeApiKey; }

    // ---------- Stripe helper ----------
    /** Always use a real cus_... id; cache it on the User row. */
    private Customer getOrCreateStripeCustomer(User user) throws StripeException {
        String cus = user.getStripeCustomerId();
        if (cus != null && !cus.isBlank()) {
            // If the cached id was deleted in Stripe, this will throw; let it bubble so we recreate if you prefer.
            return Customer.retrieve(cus);
        }
        Map<String, Object> params = new HashMap<>();
        if (user.getEmail() != null && !user.getEmail().isBlank()) params.put("email", user.getEmail());
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " +
                (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (!name.isBlank()) params.put("name", name);
        Map<String, String> md = new HashMap<>();
        md.put("wknd_user_id", String.valueOf(user.getId()));
        params.put("metadata", md);

        Customer created = Customer.create(params);
        user.setStripeCustomerId(created.getId());
        userRepository.save(user);
        return created;
    }

    // ---------- CARD (Stripe) ----------
    public Map<String, Object> createPaymentIntentForResale(Long resalePostId, Long buyerId) throws StripeException {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Resale post not found"));
        if (post.isSold()) throw new IllegalArgumentException("This post is already sold.");
        if (Objects.equals(post.getSeller().getId(), buyerId))
            throw new IllegalArgumentException("You cannot buy your own resale post");

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        long amount = Math.round(post.getPrice() * 100.0);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency("usd")
                .setReceiptEmail(buyer.getEmail()) // optional but nice for receipts
                .putMetadata("type", "resale")
                .putMetadata("resalePostId", String.valueOf(resalePostId))
                .putMetadata("buyerId", String.valueOf(buyerId))
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                )
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        return Map.of("clientSecret", intent.getClientSecret());
    }

    // Guard: event must still be in the future
    private void assertEventNotPast(ResalePost post) {
        Event e = post.getTicket() != null ? post.getTicket().getEvent() : null;
        if (e != null && e.getDateTime() != null && !e.getDateTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Event already started/past; transfer not allowed.");
        }
    }

    @Transactional
    public Ticket processPaymentAndTransferTicket(Long resalePostId, Long buyerId) {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Resale post not found"));

        if (post.isSold()) throw new IllegalArgumentException("This post is already sold.");
        if (Objects.equals(post.getSeller().getId(), buyerId))
            throw new IllegalArgumentException("You cannot buy your own resale post");

        assertEventNotPast(post);

        Ticket ticket = post.getTicket();
        if (ticket == null) throw new IllegalStateException("Ticket missing.");
        // Extra safety: ensure the seller still owns the ticket at transfer time
        if (ticket.getOwner() == null || !Objects.equals(ticket.getOwner().getId(), post.getSeller().getId())) {
            throw new IllegalStateException("Ticket is no longer owned by the seller.");
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        // Transfer ownership
        ticket.setOwner(buyer);
        ticket.setStatus(TicketStatus.PAID);
        ticket.setQrCodeUrl("/api/tickets/qr/" + ticket.getId());
        ticketRepository.save(ticket);

        // Mark post as sold (and timestamp)
        post.setSold(true);
        post.setSoldAt(LocalDateTime.now());
        resalePostRepository.save(post);

        // Notify
        notificationService.notifyResaleSold(resalePostId);
        if (ticket.getEvent() != null) {
            notificationService.notifyFriendsGoing(buyerId, ticket.getEvent().getId());
        }
        return ticket;
    }

    public void confirmResalePayment(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            if (!"succeeded".equals(intent.getStatus())) return;

            Map<String, String> metadata = intent.getMetadata();
            Long resalePostId = Long.parseLong(metadata.get("resalePostId"));
            Long buyerId      = Long.parseLong(metadata.get("buyerId"));

            Ticket ticket = processPaymentAndTransferTicket(resalePostId, buyerId);

            // ✅ Send WKND ticket email to buyer
            if (ticket != null && ticket.getOwner() != null && ticket.getEvent() != null) {
                try {
                    emailService.sendTicketEmail(
                            ticket.getOwner().getEmail(),
                            ticket.getOwner().getFirstName(),
                            ticket.getEvent(),
                            List.of(ticket)
                    );
                } catch (Exception ex) {
                    System.out.println("⚠️ Failed to send resale ticket email: " + ex.getMessage());
                }
            }

            System.out.println("✅ Resale confirmed via webhook");
        } catch (Exception e) {
            System.out.println("❌ Resale confirm webhook error: " + e.getMessage());
        }
    }


    // ---------- OPTIONS ----------
    public PaymentOptionsDTO getPaymentOptions(Long resalePostId) {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Resale post not found"));

        Event event = post.getTicket().getEvent();
        String country = event != null ? event.getCountry() : null;

        List<String> allowed = new ArrayList<>();
        allowed.add("card"); // Stripe

        boolean vene = country != null && (country.equalsIgnoreCase("Venezuela") || country.equalsIgnoreCase("VE"));
        if (vene) {
            if (!isBlank(post.getSellerZelleEmail())) allowed.add("zelle");
            if (!isBlank(post.getSellerPagoMovilPhone())
                    && !isBlank(post.getSellerPagoMovilCi())
                    && !isBlank(post.getSellerPagoMovilBank())) allowed.add("pagoMovil");
            if (!isBlank(post.getSellerCashNote())) allowed.add("cash");
        }

        return new PaymentOptionsDTO(
                country,
                "USD",
                allowed,
                post.getSellerZelleEmail(),
                post.getSellerPagoMovilPhone(),
                post.getSellerPagoMovilCi(),
                post.getSellerPagoMovilBank(),
                post.getSellerCashNote()
        );
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    // ---------- MANUAL ORDERS ----------
    public ResaleOrder createManualOrder(Long resalePostId, Long buyerId, PaymentMethod method, String reference) {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Resale post not found"));
        if (post.isSold()) throw new IllegalArgumentException("This post is already sold.");
        if (Objects.equals(post.getSeller().getId(), buyerId))
            throw new IllegalArgumentException("You cannot buy your own resale post");

        Event event = post.getTicket().getEvent();
        String country = event != null ? event.getCountry() : null;
        boolean vene = country != null && (country.equalsIgnoreCase("Venezuela") || country.equalsIgnoreCase("VE"));
        if (!vene) throw new IllegalArgumentException("Manual payment is not available for this post.");

        // Validate seller has provided details for the chosen method
        switch (method) {
            case zelle -> {
                if (isBlank(post.getSellerZelleEmail()))
                    throw new IllegalArgumentException("Seller has not provided Zelle details.");
            }
            case pagoMovil -> {
                if (isBlank(post.getSellerPagoMovilPhone())
                        || isBlank(post.getSellerPagoMovilCi())
                        || isBlank(post.getSellerPagoMovilBank()))
                    throw new IllegalArgumentException("Seller has not provided Pago Móvil details.");
            }
            case cash -> {
                if (isBlank(post.getSellerCashNote()))
                    throw new IllegalArgumentException("Seller has not provided cash instructions.");
            }
            case card -> throw new IllegalArgumentException("Use the card endpoint for card payments.");
            default -> throw new IllegalArgumentException("Unsupported manual payment method.");
        }

        ResaleOrder order = ResaleOrder.builder()
                .resalePost(post)
                .buyer(userRepository.findById(buyerId).orElseThrow(() -> new IllegalArgumentException("Buyer not found")))
                .method(method)
                .status(ResaleOrderStatus.PENDING)
                .amount(post.getPrice())
                .currency("USD")
                .reference(reference)
                .createdAt(LocalDateTime.now())
                .build();

        order = resaleOrderRepository.save(order);
        notificationService.notifyManualPaymentPending(order.getId());
        return order;
    }

    @Transactional
    public Ticket confirmManualOrder(Long orderId, Long sellerId) {
        ResaleOrder order = resaleOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Manual order not found"));
        ResalePost post = order.getResalePost();

        if (!Objects.equals(post.getSeller().getId(), sellerId)) {
            throw new IllegalArgumentException("Only the seller can confirm this order.");
        }
        if (order.getMethod() == PaymentMethod.card) {
            throw new IllegalArgumentException("Card payments are confirmed automatically.");
        }

        // Idempotency: if already paid and ticket is with buyer, just return it
        if (order.getStatus() == ResaleOrderStatus.PAID) {
            Ticket t = post.getTicket();
            if (t != null && t.getOwner() != null &&
                    Objects.equals(t.getOwner().getId(), order.getBuyer().getId())) {
                return t; // already transferred earlier
            }
            // If we reach here something is inconsistent
            throw new IllegalStateException("Order already marked PAID but ticket not owned by buyer.");
        }

        if (post.isSold()) throw new IllegalArgumentException("This post is already sold.");
        if (order.getStatus() != ResaleOrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be confirmed.");
        }

        assertEventNotPast(post);

        // Mark the order paid & timestamp it (do not set post.sold here)
        order.setStatus(ResaleOrderStatus.PAID);
        order.setConfirmedAt(LocalDateTime.now());
        resaleOrderRepository.save(order);

        // Do the transfer (sets post.sold + timestamps)
        Ticket ticket = processPaymentAndTransferTicket(post.getId(), order.getBuyer().getId());

        // Cancel any competing PENDING orders for the same post
        var competing = resaleOrderRepository.findByResalePostAndStatus(post, ResaleOrderStatus.PENDING);
        if (!competing.isEmpty()) {
            for (ResaleOrder o : competing) {
                if (!Objects.equals(o.getId(), order.getId())) {
                    o.setStatus(ResaleOrderStatus.CANCELLED);
                    o.setCancelReason("Post sold to another buyer");
                    o.setCancelledAt(LocalDateTime.now());
                }
            }
            resaleOrderRepository.saveAll(competing);
        }

        notificationService.notifyManualPaymentConfirmed(orderId);

        // ✅ Send WKND-branded ticket email to buyer
        if (ticket != null && ticket.getOwner() != null && ticket.getEvent() != null) {
            try {
                emailService.sendTicketEmail(
                        ticket.getOwner().getEmail(),
                        ticket.getOwner().getFirstName(),
                        ticket.getEvent(),
                        List.of(ticket)
                );
            } catch (Exception e) {
                System.out.println("⚠️ Failed to send manual resale ticket email: " + e.getMessage());
            }
        }

        return ticket;
    }

    // ---------- ACCEPTED OFFER FLOWS ----------
    private OfferMeta loadAcceptedOfferOrThrow(Long messageId) {
        ChatMessage m = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Offer message not found"));

        String metaJson = Optional.ofNullable(m.getMetaJson()).orElse("").trim();
        if (metaJson.isEmpty()) {
            throw new IllegalStateException("Offer metadata missing");
        }

        OfferMeta meta;
        try {
            // tolerant mapper handles unknown fields + case
            meta = objectMapper.readValue(metaJson, OfferMeta.class);
        } catch (Exception first) {
            // Fallback: parse as Map and pick known keys
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(metaJson, Map.class);
                meta = new OfferMeta();

                Object a = map.get("amount");
                if (a instanceof Number n) meta.amount = n.doubleValue();
                else if (a instanceof String s) meta.amount = Double.valueOf(s);

                Object c = map.get("currency");
                meta.currency = (c == null || c.toString().isBlank()) ? "USD" : c.toString();

                Object s0 = map.get("status");
                meta.status = (s0 == null) ? null : s0.toString();

                Object auth = map.get("authorId");
                if (auth instanceof Number n) meta.authorId = n.longValue();
                else if (auth != null) meta.authorId = Long.valueOf(auth.toString());

                Object rpid = map.get("resalePostId");
                if (rpid instanceof Number n) meta.resalePostId = n.longValue();
                else if (rpid != null) meta.resalePostId = Long.valueOf(rpid.toString());

            } catch (Exception second) {
                throw new IllegalStateException("Invalid offer metadata");
            }
        }

        // Defaults/validations
        if (meta.currency == null || meta.currency.isBlank()) meta.currency = "USD";
        if (meta.amount == null || meta.amount <= 0) {
            throw new IllegalStateException("Offer amount invalid");
        }
        if (meta.status == null || !meta.status.equalsIgnoreCase("ACCEPTED")) {
            throw new IllegalStateException("Offer is not ACCEPTED");
        }

        // If old messages missed resalePostId, rescue it from the room itself
        if (meta.resalePostId == null && m.getChat() != null) {
            Long fromRoom = m.getChat().getResalePostId();
            if (fromRoom != null) meta.resalePostId = fromRoom;
        }
        if (meta.resalePostId == null) {
            throw new IllegalStateException("Offer missing resalePostId");
        }

        return meta;
    }

    @Transactional
    public String createPaymentIntentForAcceptedOffer(Long messageId, Long buyerId) throws Exception {
        OfferMeta meta = loadAcceptedOfferOrThrow(messageId);
        if (meta.authorId == null || !meta.authorId.equals(buyerId)) {
            throw new IllegalStateException("Only the offer author can pay this offer");
        }
        ResalePost post = resalePostRepository.findById(meta.resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Resale post not found"));

        assertEventNotPast(post);

        long amountInMinor = Math.round(meta.amount * 100);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInMinor)
                .setCurrency(meta.currency.toLowerCase())
                .putMetadata("resalePostId", String.valueOf(post.getId()))
                .putMetadata("offerMessageId", String.valueOf(messageId))
                .putMetadata("buyerId", String.valueOf(buyerId))
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                )
                .build();

        PaymentIntent pi = PaymentIntent.create(params);
        return pi.getClientSecret();
    }

    @Transactional
    public Long createManualOrderForAcceptedOffer(Long messageId, Long buyerId, String method, String reference) {
        OfferMeta meta = loadAcceptedOfferOrThrow(messageId);
        if (meta.authorId == null || !meta.authorId.equals(buyerId)) {
            throw new IllegalStateException("Only the offer author can pay this offer");
        }
        // Reuse existing validations & gating by calling your normal manual creator:
        ResaleOrder order = createManualOrder(meta.resalePostId, buyerId, PaymentMethod.from(method), reference);
        // Optionally tie the order to the offer message if you track that:
        // order.setOfferMessageId(messageId);
        // resaleOrderRepository.save(order);
        return order.getId();
    }

    // after a successful transfer in the offer path
    @Transactional
    public void confirmTransferForAcceptedOffer(Long messageId, Long buyerId) {
        OfferMeta meta = loadAcceptedOfferOrThrow(messageId);
        if (meta.authorId == null || !meta.authorId.equals(buyerId)) {
            throw new IllegalStateException("Only the offer author can confirm this offer");
        }
        processPaymentAndTransferTicket(meta.resalePostId, buyerId);

        // 👇 announce SOLD to the room & update the offer bubble
        try { chatService.markOfferSold(messageId); } catch (Exception ignored) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class OfferMeta {
        public Double amount;
        public String currency;
        public String status;     // PENDING / ACCEPTED / DECLINED
        public Long   authorId;   // offer author (buyer)
        public Long   resalePostId;
    }

}
