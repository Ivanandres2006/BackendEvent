package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.PaymentIntentResponse;
import com.example.dbeventservice.dto.PaymentOptionsDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.model.Enum.PaymentMethod;
import com.example.dbeventservice.model.Enum.ResaleOrderStatus;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.*;
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

    @PostConstruct
    public void init() { Stripe.apiKey = stripeApiKey; }

    // ---------- CARD (Stripe) ----------
    public PaymentIntentResponse createPaymentIntentForResale(Long resalePostId, Long buyerId) throws StripeException {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Resale post not found"));
        if (post.isSold()) throw new IllegalArgumentException("This post is already sold.");
        if (Objects.equals(post.getSeller().getId(), buyerId))
            throw new IllegalArgumentException("You cannot buy your own resale post");

        // Create/retrieve Stripe customer for buyer (id = buyerId)
        Customer customer = Customer.retrieve(String.valueOf(buyerId));
        if (customer == null || customer.getId() == null) {
            Map<String, Object> params = new HashMap<>();
            params.put("id", String.valueOf(buyerId));
            customer = Customer.create(params);
        }

        RequestOptions keyOpts = RequestOptions.builder().setStripeVersionOverride("2022-11-15").build();
        Map<String, Object> ekParams = new HashMap<>();
        ekParams.put("customer", customer.getId());
        EphemeralKey ephemeralKey = EphemeralKey.create(ekParams, keyOpts);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "resale");
        metadata.put("resalePostId", String.valueOf(resalePostId));
        metadata.put("buyerId", String.valueOf(buyerId));

        PaymentIntentCreateParams createParams =
                PaymentIntentCreateParams.builder()
                        .setAmount(Math.round(post.getPrice() * 100)) // cents
                        .setCurrency("usd")
                        .setCustomer(customer.getId())
                        .putAllMetadata(metadata)
                        .build();

        PaymentIntent intent = PaymentIntent.create(createParams);

        return new PaymentIntentResponse(
                intent.getClientSecret(),
                ephemeralKey.getSecret(),
                customer.getId()
        );
    }

    public Ticket processPaymentAndTransferTicket(Long resalePostId, Long buyerId) {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Resale post not found"));

        if (post.isSold()) throw new IllegalArgumentException("This post is already sold.");
        if (Objects.equals(post.getSeller().getId(), buyerId))
            throw new IllegalArgumentException("You cannot buy your own resale post");

        Ticket ticket = post.getTicket();
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        // Transfer ownership
        ticket.setOwner(buyer);
        ticket.setStatus(TicketStatus.PAID);
        ticket.setQrCodeUrl("/api/tickets/qr/" + ticket.getId());
        ticketRepository.save(ticket);

        // Mark post as sold
        post.setSold(true);
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

            processPaymentAndTransferTicket(resalePostId, buyerId);
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
        String country = (event != null) ? event.getCountry() : null;

        // What the buyer can choose
        List<String> allowed = new ArrayList<>();
        allowed.add("card"); // Stripe is always allowed

        // Manual methods only in VE (keep your existing rule)
        boolean vene = country != null &&
                (country.equalsIgnoreCase("Venezuela") || country.equalsIgnoreCase("VE"));
        if (vene) {
            if (!isBlank(post.getSellerZelleEmail())) {
                allowed.add("zelle");
            }
            if (!isBlank(post.getSellerPagoMovilPhone())
                    && !isBlank(post.getSellerPagoMovilCi())
                    && !isBlank(post.getSellerPagoMovilBank())) {
                allowed.add("pagoMovil");
            }
            if (!isBlank(post.getSellerCashNote())) {
                allowed.add("cash");
            }
        }

        // Return *seller* details (not event details)
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


    private String nb(String s) { return (s == null || s.isBlank()) ? null : s; }


    // ---------- MANUAL ORDERS ----------
    public ResaleOrder createManualOrder(Long resalePostId, Long buyerId,
                                         PaymentMethod method, String reference) {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Resale post not found"));

        if (post.isSold()) throw new IllegalArgumentException("This post is already sold.");
        if (Objects.equals(post.getSeller().getId(), buyerId))
            throw new IllegalArgumentException("You cannot buy your own resale post");

        Event event = post.getTicket().getEvent();
        String country = (event != null) ? event.getCountry() : null;
        boolean vene = country != null &&
                (country.equalsIgnoreCase("Venezuela") || country.equalsIgnoreCase("VE"));

        // Keep your Venezuela-only rule for manual flows
        if (!vene) throw new IllegalArgumentException("Manual payment is not available for this post.");

        // Validate the chosen manual method against *seller* fields
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

        // Build order
        ResaleOrder order = ResaleOrder.builder()
                .resalePost(post)
                .buyer(userRepository.findById(buyerId)
                        .orElseThrow(() -> new IllegalArgumentException("Buyer not found")))
                .method(method)
                .status(ResaleOrderStatus.PENDING)
                .amount(post.getPrice())
                .currency("USD")
                .reference(reference)
                .createdAt(LocalDateTime.now())
                .build();

        order = resaleOrderRepository.save(order);

        // Notify seller
        notificationService.notifyManualPaymentPending(order.getId());

        return order;
    }


    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }




    public Ticket confirmManualOrder(Long orderId, Long sellerId) {
        ResaleOrder order = resaleOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Manual order not found"));

        ResalePost post = order.getResalePost();
        if (!Objects.equals(post.getSeller().getId(), sellerId)) {
            throw new IllegalArgumentException("Only the seller can confirm this order.");
        }
        if (post.isSold()) throw new IllegalArgumentException("This post is already sold.");

        order.setStatus(ResaleOrderStatus.PAID);
        var posted = order.getResalePost();
        posted.setSold(true);
        posted.setSoldAt(LocalDateTime.now());   // 🔽 NEW
        postRepo.save(posted);
        order.setConfirmedAt(LocalDateTime.now());
        resaleOrderRepository.save(order);

        notificationService.notifyManualPaymentConfirmed(orderId);

        return processPaymentAndTransferTicket(post.getId(), order.getBuyer().getId());
    }
}
