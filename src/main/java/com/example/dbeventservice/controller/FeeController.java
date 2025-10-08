package com.example.dbeventservice.controller;

import com.example.dbeventservice.dto.DiscountResult;
import com.example.dbeventservice.dto.FeeBreakdown;
import com.example.dbeventservice.model.Enum.PaymentMethod;
import com.example.dbeventservice.service.DiscountService;
import com.example.dbeventservice.service.FeeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tickets")
public class FeeController {

    private final FeeCalculator feeCalculator;
    private final DiscountService discountService;

    @GetMapping("/quote")
    public ResponseEntity<FeeBreakdown> quote(
            @RequestParam long eventId,
            @RequestParam long ticketTierId,
            @RequestParam int quantity,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String discountCode,
            @RequestParam long unitPriceCents // you may load price from DB instead
    ) {
        long originalSubtotalCents = unitPriceCents * (long) quantity;

        DiscountResult dr = null;
        if (discountCode != null && !discountCode.isBlank()) {
            dr = discountService.validateAndCompute(discountCode, unitPriceCents, quantity, eventId, ticketTierId);
        }

        long discountedSubtotal = originalSubtotalCents;
        if (dr != null && dr.isValid()) {
            discountedSubtotal = Math.max(0, originalSubtotalCents - dr.getDiscountCents());
        }

        PaymentMethod method = PaymentMethod.valueOf(paymentMethod); // "card", "zelle", etc.
        // Feed discounted subtotal to fee calc (aggregate as a single amount)
        FeeBreakdown fee = feeCalculator.quote(discountedSubtotal, 1, method);

        // Decorate with discount metadata (optional)
        fee.setOriginalSubtotalCents(originalSubtotalCents);
        if (dr != null) {
            if (dr.isValid()) {
                fee.setDiscountCentsApplied((long) dr.getDiscountCents());
                fee.setDiscountCodeApplied(dr.getCodeApplied());
            }
            boolean hasCap = dr.getRemainingTickets() != Integer.MAX_VALUE;
            String msg = dr.isValid()
                    ? (hasCap ? dr.getMessage() + " · " + dr.getRemainingTickets() + " left" : dr.getMessage())
                    : dr.getMessage();
            fee.setDiscountMessage(msg);
        }

        return ResponseEntity.ok(fee);
    }
}
