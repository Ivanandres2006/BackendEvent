package com.example.dbeventservice.controller;

import com.example.dbeventservice.dto.FeeBreakdown;
import com.example.dbeventservice.model.Enum.PaymentMethod;
import com.example.dbeventservice.model.TicketTier;
import com.example.dbeventservice.repositories.TicketTierRepository;
import com.example.dbeventservice.service.DiscountService;
import com.example.dbeventservice.service.FeeCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
public class FeeController {

    @Autowired private TicketTierRepository ticketTierRepository;
    @Autowired private FeeCalculator feeCalculator;

    @Autowired private DiscountService discountService;

    @GetMapping("/quote")
    public ResponseEntity<?> quoteFees(
            @RequestParam Long eventId,
            @RequestParam Long ticketTierId,
            @RequestParam int quantity,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String discountCode // NEW
    ) {
        var tierOpt = ticketTierRepository.findById(ticketTierId);
        if (tierOpt.isEmpty()) return ResponseEntity.status(404).body(java.util.Map.of("error","Tier not found"));

        var tier = tierOpt.get();
        long unitPriceCents = Math.round((tier.getPrice()==null?0.0:tier.getPrice())*100.0);
        long originalSubtotal = unitPriceCents * quantity;

        // Evaluate discount
        var res = discountService.evaluate(eventId, ticketTierId, quantity, originalSubtotal, discountCode);
        long subtotalAfterDiscount = originalSubtotal - (res.isValid() ? res.getDiscountCents() : 0);

        var breakdown = feeCalculator.quote(subtotalAfterDiscount / quantity, quantity, PaymentMethod.from(paymentMethod));

        // decorate with discount fields
        breakdown.setOriginalSubtotalCents(originalSubtotal);
        breakdown.setDiscountCentsApplied(res.isValid() ? res.getDiscountCents() : 0L);
        breakdown.setDiscountCodeApplied(res.isValid() ? res.getCodeApplied() : null);
        breakdown.setDiscountMessage(res.isValid() ? res.getMessage() : null);

        return ResponseEntity.ok(breakdown);
    }
}
