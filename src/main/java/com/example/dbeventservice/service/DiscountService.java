// src/main/java/com/example/dbeventservice/service/DiscountService.java
package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.DiscountResult;
import com.example.dbeventservice.model.DiscountCode;
import com.example.dbeventservice.repositories.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DiscountService {
    private final DiscountCodeRepository repo;

    public DiscountResult evaluate(Long eventId, Long ticketTierId, int qty, long subtotalCents, String codeRaw) {
        if (codeRaw == null || codeRaw.isBlank()) return new DiscountResult(false, "No code", null, 0);
        String code = codeRaw.trim().toUpperCase();

        Optional<DiscountCode> opt = repo.findByEventIdAndCodeIgnoreCase(eventId, code);
        if (opt.isEmpty()) return new DiscountResult(false, "Invalid code", null, 0);

        DiscountCode dc = opt.get();
        if (Boolean.FALSE.equals(dc.getActive())) return new DiscountResult(false, "Code inactive", null, 0);

        Instant now = Instant.now();
        if (dc.getStartsAt()!=null && now.isBefore(dc.getStartsAt())) return new DiscountResult(false,"Not started",null,0);
        if (dc.getEndsAt()!=null && now.isAfter(dc.getEndsAt())) return new DiscountResult(false,"Expired",null,0);

        if (dc.getMaxUses()!=null && dc.getUsedCount()!=null && dc.getUsedCount() >= dc.getMaxUses())
            return new DiscountResult(false,"Usage limit reached",null,0);

        if (dc.getMinQty()!=null && qty < dc.getMinQty()) return new DiscountResult(false,"Min quantity not met",null,0);
        if (dc.getMaxQty()!=null && qty > dc.getMaxQty()) return new DiscountResult(false,"Max quantity exceeded",null,0);

        if (dc.getAppliesToTicketTierId()!=null && !dc.getAppliesToTicketTierId().equals(ticketTierId))
            return new DiscountResult(false,"Not valid for this tier",null,0);

        long discountCents = 0;
        String message;
        if (dc.getType() == DiscountCode.Type.PERCENT) {
            BigDecimal pct = BigDecimal.valueOf(dc.getValue() / 100.0);
            discountCents = BigDecimal.valueOf(subtotalCents)
                    .multiply(pct)
                    .setScale(0, RoundingMode.FLOOR).longValue(); // floor to avoid over-discount
            message = ((int)Math.round(dc.getValue())) + "% off";
        } else {
            // fixed USD -> to cents
            long amtCents = BigDecimal.valueOf(dc.getValue()).movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            discountCents = Math.min(amtCents, subtotalCents);
            message = String.format("$%.2f off", dc.getValue());
        }

        if (discountCents <= 0) return new DiscountResult(false,"No discount",null,0);
        return new DiscountResult(true, message, code, discountCents);
    }

    /** Call once payment succeeded. */
    public void consumeUsageIfExists(Long eventId, String codeRaw) {
        if (codeRaw == null || codeRaw.isBlank()) return;
        repo.findByEventIdAndCodeIgnoreCase(eventId, codeRaw.trim())
                .ifPresent(dc -> {
                    dc.setUsedCount((dc.getUsedCount()==null?0:dc.getUsedCount()) + 1);
                    repo.save(dc);
                });
    }
}
