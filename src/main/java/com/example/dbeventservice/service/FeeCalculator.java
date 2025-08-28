package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.FeeBreakdown;
import com.example.dbeventservice.model.Enum.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes a single "Service fee" for buyers.
 * - All methods: include your platform % on the ticket subtotal.
 * - Card/Apple Pay: also include Stripe fee (pct + fixed) into the SAME line.
 * Rounds UP to avoid under-collecting fees.
 */
@Component
public class FeeCalculator {

    private final BigDecimal servicePct; // platform %
    private final BigDecimal stripePct;  // processor %
    private final int stripeFixedCents;  // processor fixed

    public FeeCalculator(
            @Value("${service.fee.pct:0.08}") double servicePct,
            @Value("${stripe.fee.pct:0.029}") double stripePct,
            @Value("${stripe.fee.fixed_cents:30}") int stripeFixedCents
    ) {
        this.servicePct = BigDecimal.valueOf(servicePct);
        this.stripePct = BigDecimal.valueOf(stripePct);
        this.stripeFixedCents = stripeFixedCents;
    }

    public FeeBreakdown quote(long unitPriceCents, int qty, PaymentMethod method) {
        long subtotalCents = unitPriceCents * qty;

        // Platform fee = ceil(subtotal * servicePct)
        long platformFee = ceilCents(cents(subtotalCents).multiply(servicePct));

        // Stripe fee (only for card): pct * T + fixed, where T is the TOTAL paid.
        long stripeFee = 0;
        if (method == PaymentMethod.card) {
            // T = (subtotal + platform + fixed) / (1 - pct)
            BigDecimal subtotal = cents(subtotalCents);
            BigDecimal platform = cents(platformFee);
            BigDecimal fixed = cents(stripeFixedCents);
            BigDecimal oneMinus = BigDecimal.ONE.subtract(stripePct);

            BigDecimal T = subtotal.add(platform).add(fixed)
                    .divide(oneMinus, 2, RoundingMode.CEILING);

            BigDecimal stripeVar = T.multiply(stripePct).setScale(2, RoundingMode.CEILING);
            stripeFee = toCents(stripeVar.add(fixed)); // pct*T + fixed
        }

        long serviceFee = platformFee + stripeFee;
        long total = subtotalCents + serviceFee;

        return new FeeBreakdown(
                "USD",
                subtotalCents,
                platformFee,
                stripeFee,
                serviceFee,
                total,
                servicePct.doubleValue(),
                stripePct.doubleValue(),
                stripeFixedCents
        );
    }

    // ----- money helpers -----
    private static BigDecimal cents(long c) {
        return new BigDecimal(c).divide(new BigDecimal(100), 2, RoundingMode.UNNECESSARY);
    }
    private static long toCents(BigDecimal dollars) {
        return dollars.movePointRight(2).setScale(0, RoundingMode.CEILING).longValueExact();
    }
    private static long ceilCents(BigDecimal dollars) {
        return dollars.movePointRight(2).setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
