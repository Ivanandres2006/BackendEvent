package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.service.ResalePaymentService;
import com.example.dbeventservice.service.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final TicketService ticketService;
    private final ResalePaymentService resalePaymentService;

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(HttpServletRequest request) {
        try {
            String payload = new BufferedReader(new InputStreamReader(request.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            String sigHeader = request.getHeader("Stripe-Signature");

            if (stripeWebhookSecret == null || stripeWebhookSecret.isEmpty()) {
                return ResponseEntity.status(500).body("Missing Stripe webhook secret");
            }

            Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
            System.out.println("✅ Webhook type: " + event.getType());

            if ("payment_intent.succeeded".equals(event.getType())) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode root = objectMapper.readTree(payload);
                JsonNode paymentIntentData = root.path("data").path("object");

                String paymentIntentId = paymentIntentData.path("id").asText();
                JsonNode metadata = paymentIntentData.path("metadata");

                System.out.println("✅ Webhook PaymentIntent ID: " + paymentIntentId);

                if (metadata.has("eventId")) {
                    ticketService.confirmPayment(paymentIntentId);
                } else if (metadata.has("resalePostId")) {
                    resalePaymentService.confirmResalePayment(paymentIntentId);
                } else {
                    System.out.println("⚠️ Unknown payment intent type. Skipping.");
                }
            }

            return ResponseEntity.ok("Webhook processed");

        } catch (SignatureVerificationException e) {
            System.out.println("❌ Stripe signature verification failed: " + e.getMessage());
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            System.out.println("❌ Webhook error: " + e.getMessage());
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
    }

    @PostMapping("/reunion-checkout")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> reunionCheckout(
            @RequestParam Long eventId,
            @RequestParam String email,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        try {
            Map<String, Object> response = ticketService.createReunionCheckout(
                    userDetails.getId(),
                    eventId,
                    email
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

}

