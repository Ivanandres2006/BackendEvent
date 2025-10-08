// src/main/java/com/example/dbeventservice/controller/DiscountController.java
package com.example.dbeventservice.controller;

import com.example.dbeventservice.dto.DiscountDTO;
import com.example.dbeventservice.dto.DiscountResult;
import com.example.dbeventservice.model.DiscountCode;
import com.example.dbeventservice.repositories.DiscountCodeRepository;
import com.example.dbeventservice.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountCodeRepository repo;
    private final DiscountService discountService;

    @GetMapping
    public List<DiscountCode> list(@RequestParam Long eventId) {
        return repo.findByEventIdOrderByIdDesc(eventId);
    }

    @GetMapping("/validate")
    public DiscountResult validate(
            @RequestParam Long eventId,
            @RequestParam Long ticketTierId,
            @RequestParam int quantity,
            @RequestParam long subtotalCents,
            @RequestParam String code
    ) {
        long unitPriceCents = 0L;
        if (quantity > 0) {
            unitPriceCents = BigDecimal
                    .valueOf(subtotalCents)
                    .divide(BigDecimal.valueOf(quantity), 0, BigDecimal.ROUND_HALF_UP)
                    .longValue();
        }
        return discountService.validateAndCompute(code, unitPriceCents, quantity, eventId, ticketTierId);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody DiscountDTO dto) {
        // require value on create (since client must send it once)
        if (!dto.isValuePresent()) {
            return ResponseEntity.badRequest().body("Field 'value' is required.");
        }
        if (dto.getType() == null) {
            return ResponseEntity.badRequest().body("Field 'type' is required (PERCENT or AMOUNT).");
        }
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            return ResponseEntity.badRequest().body("Field 'code' is required.");
        }
        if (dto.getEventId() == null) {
            return ResponseEntity.badRequest().body("Field 'eventId' is required.");
        }

        DiscountCode dc = new DiscountCode();
        dc.setEventId(dto.getEventId());
        dc.setCode(dto.getCode().trim().toUpperCase());
        dc.setType(DiscountCode.Type.valueOf(dto.getType()));
        dc.setValue(dto.getValue()); // safe: required on create

        dc.setAppliesToTicketTierId(dto.getAppliesToTicketTierId());
        dc.setMaxUses(dto.getMaxUses());
        dc.setUsedCount(0);
        dc.setMinQty(dto.getMinQty());
        dc.setMaxQty(dto.getMaxQty());
        dc.setStartsAt(dto.getStartsAt());
        dc.setEndsAt(dto.getEndsAt());
        dc.setActive(dto.getActive() == null ? Boolean.TRUE : dto.getActive());

        // optional ticket cap
        dc.setTicketLimit(dto.getTicketLimit());
        dc.setTicketsRedeemed(0);

        DiscountCode saved = repo.save(dc);
        return ResponseEntity.created(URI.create("/api/discounts/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody DiscountDTO dto) {
        DiscountCode dc = repo.findById(id).orElse(null);
        if (dc == null) return ResponseEntity.notFound().build();

        if (dto.getCode() != null) dc.setCode(dto.getCode().trim().toUpperCase());
        if (dto.getType() != null) dc.setType(DiscountCode.Type.valueOf(dto.getType()));
        if (dto.isValuePresent())   dc.setValue(dto.getValue()); // ← use presence flag

        dc.setAppliesToTicketTierId(dto.getAppliesToTicketTierId());
        dc.setMaxUses(dto.getMaxUses());
        dc.setMinQty(dto.getMinQty());
        dc.setMaxQty(dto.getMaxQty());
        dc.setStartsAt(dto.getStartsAt());
        dc.setEndsAt(dto.getEndsAt());
        if (dto.getActive() != null) dc.setActive(dto.getActive());

        // Optional ticket cap
        dc.setTicketLimit(dto.getTicketLimit());

        repo.save(dc);
        return ResponseEntity.ok(dc);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        DiscountCode dc = repo.findById(id).orElse(null);
        if (dc == null) return ResponseEntity.notFound().build();
        repo.delete(dc);
        return ResponseEntity.noContent().build();
    }
}
