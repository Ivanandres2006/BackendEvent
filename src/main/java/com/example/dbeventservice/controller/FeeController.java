package com.example.dbeventservice.controller;

import com.example.dbeventservice.dto.FeeBreakdown;
import com.example.dbeventservice.model.Enum.PaymentMethod;
import com.example.dbeventservice.model.TicketTier;
import com.example.dbeventservice.repositories.TicketTierRepository;
import com.example.dbeventservice.service.FeeCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
public class FeeController {

    @Autowired private TicketTierRepository ticketTierRepository;
    @Autowired private FeeCalculator feeCalculator;

    @GetMapping("/quote")
    public ResponseEntity<?> quoteFees(
            @RequestParam Long eventId,
            @RequestParam Long ticketTierId,
            @RequestParam int quantity,
            @RequestParam String paymentMethod
    ) {
        var tierOpt = ticketTierRepository.findById(ticketTierId);
        if (tierOpt.isEmpty()) return ResponseEntity.status(404).body(java.util.Map.of("error", "Tier not found"));

        TicketTier tier = tierOpt.get();
        long unitPriceCents = Math.round((tier.getPrice() == null ? 0.0 : tier.getPrice()) * 100.0);

        FeeBreakdown breakdown = feeCalculator.quote(unitPriceCents, quantity, PaymentMethod.from(paymentMethod));
        return ResponseEntity.ok(breakdown);
    }
}
