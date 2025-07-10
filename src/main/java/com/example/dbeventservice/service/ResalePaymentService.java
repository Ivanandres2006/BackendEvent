package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.PaymentIntentResponse;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.model.Enum.OfferStatus;
import com.example.dbeventservice.model.Enum.TicketStatus;
import com.example.dbeventservice.repositories.*;
import com.example.dbeventservice.util.QRCodeGenerator;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ResalePaymentService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    private final ResalePostRepository resalePostRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private CounterOfferRepository counterOfferRepository;


    public ResalePaymentService(
            ResalePostRepository resalePostRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            CounterOfferRepository counterOfferRepository
    ) {
        this.resalePostRepository = resalePostRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.counterOfferRepository = counterOfferRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public PaymentIntentResponse createPaymentIntentForResale(Long resalePostId, Long buyerId) throws StripeException {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        if (post.isSold()) throw new IllegalArgumentException("Ticket already sold");

        if (Objects.equals(post.getSeller().getId(), buyerId))
            throw new IllegalArgumentException("You cannot buy your own resale post");

        // ✅ Find chat room
        ChatRoom chatRoom = chatRoomRepository
                .findByResalePostIdAndBuyerId(resalePostId, buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));

        // ✅ Check for accepted counteroffer
        Optional<CounterOffer> accepted = counterOfferRepository
                .findTopByChatRoomAndSenderIdAndStatusOrderByCreatedAtDesc(
                        chatRoom, buyerId, OfferStatus.ACCEPTED
                );

        double finalPrice = accepted.map(CounterOffer::getAmount).orElse(post.getPrice());
        long amount = (long) (finalPrice * 100); // in cents

        // ✅ Stripe Customer
        Customer customer = Customer.create(new HashMap<>());

        EphemeralKey key = EphemeralKey.create(
                Map.of("customer", customer.getId()),
                RequestOptions.builder()
                        .setStripeVersionOverride("2023-10-16")
                        .build()
        );

        // ✅ Create PaymentIntent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency("usd")
                .setCustomer(customer.getId())
                .putMetadata("resalePostId", resalePostId.toString())
                .putMetadata("buyerId", buyerId.toString())
                .putMetadata("ticketTitle", post.getTitle())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        PaymentIntent intent = PaymentIntent.create(params);

        return new PaymentIntentResponse(
                intent.getClientSecret(),
                customer.getId(),
                key.getSecret()
        );
    }

    public Ticket processPaymentAndTransferTicket(Long resalePostId, Long buyerId) {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        if (post.isSold()) throw new IllegalArgumentException("Ticket already sold");

        if (Objects.equals(post.getSeller().getId(), buyerId))
            throw new IllegalArgumentException("You cannot buy your own resale post");

        Ticket ticket = post.getTicket();

        // ✅ Transfer ownership
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));


        ticket.setOwner(buyer);
        ticket.setStatus(TicketStatus.PAID);
        ticket.setQrCodeUrl(QRCodeGenerator.generate(ticket.getId(), buyer.getEmail())); // ✅ regenerate QR
        ticketRepository.save(ticket);


        post.setSold(true);
        resalePostRepository.save(post);

        return ticket;
    }

    public void confirmResalePayment(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            if (!"succeeded".equals(intent.getStatus())) return;

            Map<String, String> metadata = intent.getMetadata();
            Long resalePostId = Long.parseLong(metadata.get("resalePostId"));
            Long buyerId = Long.parseLong(metadata.get("buyerId"));

            processPaymentAndTransferTicket(resalePostId, buyerId);
            System.out.println("✅ Resale confirmed via webhook");

        } catch (Exception e) {
            System.out.println("❌ Resale confirm webhook error: " + e.getMessage());
        }
    }
}
