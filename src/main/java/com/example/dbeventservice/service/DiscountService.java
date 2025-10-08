package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.DiscountResult;
import com.example.dbeventservice.model.DiscountCode;
import com.example.dbeventservice.model.DiscountCode.Type;
import com.example.dbeventservice.repositories.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodeRepository repo;

    @Transactional(readOnly = true)
    public DiscountResult validateAndCompute(
            String code,
            long unitPriceCents,
            int quantity,
            Long eventId,
            Long tierId
    ) {
        if (code == null || code.isBlank()) {
            return new DiscountResult(false, "Missing code", null, 0, 0);
        }

        Optional<DiscountCode> opt = repo.findActiveByCodeAndEvent(code.trim().toUpperCase(), eventId);
        if (opt.isEmpty()) {
            return new DiscountResult(false, "Invalid code", null, 0, 0);
        }

        DiscountCode d = opt.get();

        if (!d.isActiveNow()) {
            return new DiscountResult(false, "Code is not active", d.getCode(), 0, Math.max(d.remainingTickets(), 0));
        }
        if (d.getAppliesToTicketTierId() != null && !d.getAppliesToTicketTierId().equals(tierId)) {
            return new DiscountResult(false, "Code does not apply to this tier", d.getCode(), 0, Math.max(d.remainingTickets(), 0));
        }
        if (d.getMinQty() != null && quantity < d.getMinQty()) {
            return new DiscountResult(false, "Minimum quantity: " + d.getMinQty(), d.getCode(), 0, Math.max(d.remainingTickets(), 0));
        }
        if (d.getMaxQty() != null && quantity > d.getMaxQty()) {
            return new DiscountResult(false, "Maximum quantity: " + d.getMaxQty(), d.getCode(), 0, Math.max(d.remainingTickets(), 0));
        }
        if (d.getMaxUses() != null) {
            int used = d.getUsedCount() == null ? 0 : d.getUsedCount();
            if (used >= d.getMaxUses()) {
                return new DiscountResult(false, "Code usage limit reached", d.getCode(), 0, Math.max(d.remainingTickets(), 0));
            }
        }

        // Optional ticket cap across redemptions
        int remaining = d.remainingTickets();
        boolean hasCap = d.getTicketLimit() != null;
        if (hasCap && remaining < quantity) {
            String m = remaining == 0
                    ? "This code has no discounted tickets left."
                    : "Only " + remaining + " discounted tickets left for this code.";
            return new DiscountResult(false, m, d.getCode(), 0, Math.max(remaining, 0));
        }

        long originalSubtotal = unitPriceCents * (long) quantity;

        long discountCents;
        if (d.getType() == Type.PERCENT) {
            BigDecimal pct = BigDecimal.valueOf(d.getValue()).movePointLeft(2);
            discountCents = toCents(cents(originalSubtotal).multiply(pct));
        } else { // AMOUNT per ticket
            BigDecimal perTicket = BigDecimal.valueOf(d.getValue());
            discountCents = toCents(perTicket.multiply(BigDecimal.valueOf(quantity)));
        }
        discountCents = Math.min(discountCents, originalSubtotal);

        String msg = d.getType() == Type.PERCENT
                ? (int) d.getValue() + "% off"
                : String.format("$%.2f off", d.getValue());

        return new DiscountResult(true, msg, d.getCode(), (int) discountCents, remaining);
    }

    @Transactional
    public void recordDiscountRedemption(String code, long eventId, int quantity) {
        repo.findActiveByCodeAndEvent(code.trim().toUpperCase(), eventId).ifPresent(dc -> {
            // increment "uses" if you track per-order usage
            if (dc.getMaxUses() != null) {
                int used = dc.getUsedCount() == null ? 0 : dc.getUsedCount();
                dc.setUsedCount(used + 1);
            }
            // increment ticket cap only if present
            dc.addRedemption(quantity); // no-op when unlimited
            repo.save(dc);
        });
    }

    // helpers
    private static BigDecimal cents(long c) {
        return new BigDecimal(c).divide(new BigDecimal(100));
    }
    private static long toCents(BigDecimal dollars) {
        return dollars.movePointRight(2).setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
