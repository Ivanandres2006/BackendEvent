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
        String country = event != null ? event.getCountry() : null;

        List<String> allowed = new ArrayList<>();
        allowed.add("card"); // Stripe

        // Venezuela-only methods
        if (country != null && (country.equalsIgnoreCase("Venezuela") || country.equalsIgnoreCase("VE"))) {
            if (event.getZelleEmail() != null && !event.getZelleEmail().isBlank()) allowed.add("zelle");
            if (event.getPagoMovilPhone() != null && !event.getPagoMovilPhone().isBlank()) allowed.add("pagoMovil");
            if (event.getCashNote() != null && !event.getCashNote().isBlank()) allowed.add("cash");
        }

        // You can decide currency presentation here; default USD
        String currency = (country != null && (country.equalsIgnoreCase("Venezuela") || country.equalsIgnoreCase("VE")))
                ? "USD" : "USD";

        return new PaymentOptionsDTO(
                country,
                currency,
                allowed,
                event != null ? event.getZelleEmail() : null,
                event != null ? event.getPagoMovilPhone() : null,
                event != null ? event.getPagoMovilCi() : null,
                event != null ? event.getPagoMovilBank() : null,
                event != null ? event.getCashNote() : null
        );
    }

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

        // Only allow manual in Venezuela
        if (!vene) throw new IllegalArgumentException("Manual payment is not available for this post.");

        // Build order
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

        // ping seller
        notificationService.notifyManualPaymentPending(order.getId());

        return order;
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
