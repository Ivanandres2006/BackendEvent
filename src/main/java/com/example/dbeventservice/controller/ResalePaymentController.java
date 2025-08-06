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

@RestController
@RequestMapping("/api/resale")
public class ResalePaymentController {

    @Autowired
    private ResalePaymentService resalePaymentService;

    // ✅ Step 1: Create Stripe PaymentIntent
    @PostMapping("/pay/{resalePostId}")
    public ResponseEntity<?> payForResale(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long resalePostId) {
        try {
            PaymentIntentResponse response = resalePaymentService.createPaymentIntentForResale(
                    resalePostId, userDetails.getId()
            );
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            return ResponseEntity.status(500).body("Stripe error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/confirm-transfer/{resalePostId}")
    public ResponseEntity<?> confirmTransfer(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long resalePostId) {
        try {
            Ticket ticket = resalePaymentService.processPaymentAndTransferTicket(
                    resalePostId, userDetails.getId()
            );
            TicketDTO dto = new TicketDTO(ticket, true);  // or false if you want, but likely true for resold
            return ResponseEntity.ok(dto);  // <- ✅ Return DTO, not entity!
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
