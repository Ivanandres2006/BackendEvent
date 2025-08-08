package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.PaymentIntentResponse;
import com.example.dbeventservice.dto.TicketDTO;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.service.ResalePaymentService;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/resale")
public class ResalePaymentController {

    @Autowired
    private ResalePaymentService resalePaymentService;

    @PostMapping("/pay/{resalePostId}")
    public ResponseEntity<?> payForResale(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long resalePostId
    ) {
        try {
            PaymentIntentResponse response =
                    resalePaymentService.createPaymentIntentForResale(resalePostId, userDetails.getId());
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/confirm-transfer/{resalePostId}")
    public ResponseEntity<?> confirmTransfer(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long resalePostId
    ) {
        try {
            Ticket ticket = resalePaymentService.processPaymentAndTransferTicket(resalePostId, userDetails.getId());
            return ResponseEntity.ok(new TicketDTO(ticket, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

}
