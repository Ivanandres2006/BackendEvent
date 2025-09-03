package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.MyListingDTO;
import com.example.dbeventservice.dto.PaymentIntentResponse;
import com.example.dbeventservice.dto.PaymentOptionsDTO;
import com.example.dbeventservice.dto.ResaleOrderDTO;
import com.example.dbeventservice.model.Enum.ResaleOrderStatus;
import com.example.dbeventservice.model.ResaleOrder;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.model.Enum.PaymentMethod;
import com.example.dbeventservice.repositories.ResaleOrderRepository;
import com.example.dbeventservice.repositories.ResalePostRepository;
import com.example.dbeventservice.service.ResalePaymentService;
import com.stripe.exception.StripeException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/resale")
public class ResalePaymentController {

    @Autowired
    private ResalePaymentService resalePaymentService;

    @Autowired private ResalePostRepository resalePostRepository;
    @Autowired private ResaleOrderRepository resaleOrderRepository;

    // Card intent (existing)
    // ResalePaymentController.java
    @PostMapping("/pay/{resalePostId}")
    public ResponseEntity<?> createIntent(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long resalePostId
    ) {
        try {
            var resp = resalePaymentService.createPaymentIntentForResale(resalePostId, userDetails.getId());
            return ResponseEntity.ok(resp); // => { "clientSecret": "pi_..._secret_..." }
        } catch (StripeException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    // --- SELLER: My Listings summary (with pending manual count)
    @GetMapping("/my-listings")
    public ResponseEntity<?> myListings(@AuthenticationPrincipal UserDetailsImpl user) {
        var posts = resalePostRepository.findBySeller_IdOrderByCreatedAtDesc(user.getId());
        var list = posts.stream().map(p -> {
            int pending = (int) resaleOrderRepository.countByResalePostAndStatus(p, ResaleOrderStatus.PENDING);
            return MyListingDTO.from(p, pending);
        }).toList();
        return ResponseEntity.ok(list);
    }

    // --- SELLER: Pending manual orders (optionally filter by postId)
    @GetMapping("/manual/pending")
    public ResponseEntity<?> pendingManual(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(value = "postId", required = false) Long postId
    ) {
        var status = ResaleOrderStatus.PENDING;
        var orders = (postId == null)
                ? resaleOrderRepository.findByResalePost_Seller_IdAndStatusOrderByCreatedAtDesc(user.getId(), status)
                : resaleOrderRepository.findByResalePost_IdAndResalePost_Seller_IdAndStatusOrderByCreatedAtDesc(postId, user.getId(), status);

        var dtos = orders.stream().map(ResaleOrderDTO::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @Getter @Setter
    public static class CancelRequest { public String reason; }

    @PostMapping("/manual/cancel/{orderId}")
    public ResponseEntity<?> cancelManual(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable Long orderId,
            @RequestBody CancelRequest req
    ) {
        var order = resaleOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        var post = order.getResalePost();

        if (!post.getSeller().getId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not your order"));
        }
        if (order.getStatus() != ResaleOrderStatus.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only PENDING orders can be cancelled"));
        }
        order.setStatus(ResaleOrderStatus.CANCELLED);
        order.setCancelReason(req != null ? req.reason : null);
        order.setCancelledAt(LocalDateTime.now());
        resaleOrderRepository.save(order);

        // Optional: notify buyer here (if you have a notifier)
        // notificationService.notifyManualPaymentCancelled(order.getId());

        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }



    // Confirm transfer after card succeeds (existing)
    @PostMapping("/confirm-transfer/{resalePostId}")
    public ResponseEntity<?> confirmTransfer(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long resalePostId
    ) {
        try {
            Ticket ticket = resalePaymentService.processPaymentAndTransferTicket(resalePostId, userDetails.getId());
            return ResponseEntity.ok(ticket); // your TicketDTO mapping is in TicketController
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // NEW: Get payment options (gates Venezuela methods)
    @GetMapping("/options/{resalePostId}")
    public ResponseEntity<?> getOptions(@PathVariable Long resalePostId) {
        try {
            PaymentOptionsDTO dto = resalePaymentService.getPaymentOptions(resalePostId);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // NEW: Create manual order (Pago Móvil / Zelle / Cash)
    @PostMapping("/manual/{resalePostId}")
    public ResponseEntity<?> createManual(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long resalePostId,
            @RequestBody ManualOrderRequest request
    ) {
        try {
            PaymentMethod method = PaymentMethod.from(request.method);
            ResaleOrder order = resalePaymentService.createManualOrder(resalePostId, userDetails.getId(), method, request.reference);
            return ResponseEntity.ok(Map.of("orderId", order.getId(), "status", order.getStatus().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // NEW: Seller confirms manual order -> transfers ticket
    @PostMapping("/manual/confirm/{orderId}")
    public ResponseEntity<?> confirmManual(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long orderId
    ) {
        try {
            Ticket ticket = resalePaymentService.confirmManualOrder(orderId, userDetails.getId());
            return ResponseEntity.ok(Map.of("status", "confirmed", "ticketId", ticket.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- DTO for request ---
    @Getter @Setter
    public static class ManualOrderRequest {
        public String method;   // "pagoMovil" | "zelle" | "cash"
        public String reference; // optional
    }
}
