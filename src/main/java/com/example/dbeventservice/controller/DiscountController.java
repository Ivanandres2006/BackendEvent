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

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/discounts")
@RequiredArgsConstructor
public class DiscountController {
    private final DiscountCodeRepository repo;
    private final DiscountService discountService;
    // TODO: inject EventRepository/UserSessionService to enforce ownership

    // Owner-only list
    @GetMapping
    public List<DiscountCode> list(@RequestParam Long eventId) {
        return repo.findByEventIdOrderByIdDesc(eventId);
    }

    // Validate (public)
    @GetMapping("/validate")
    public DiscountResult validate(
            @RequestParam Long eventId,
            @RequestParam Long ticketTierId,
            @RequestParam int quantity,
            @RequestParam long subtotalCents,
            @RequestParam String code
    ) {
        return discountService.evaluate(eventId, ticketTierId, quantity, subtotalCents, code);
    }

    // Create (owner only)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody DiscountDTO dto) {
        // TODO: assertOwner(dto.getEventId());
        var dc = DiscountCode.builder()
                .eventId(dto.getEventId())
                .code(dto.getCode().trim().toUpperCase())
                .type(dto.getType())
                .value(dto.getValue())
                .appliesToTicketTierId(dto.getAppliesToTicketTierId())
                .maxUses(dto.getMaxUses())
                .usedCount(0)
                .minQty(dto.getMinQty())
                .maxQty(dto.getMaxQty())
                .startsAt(dto.getStartsAt())
                .endsAt(dto.getEndsAt())
                .active(dto.getActive() == null ? Boolean.TRUE : dto.getActive())
                .build();
        var saved = repo.save(dc);
        return ResponseEntity.created(URI.create("/api/discounts/" + saved.getId())).body(saved);
    }

    // Update (owner only)
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody DiscountDTO dto) {
        var dc = repo.findById(id).orElse(null);
        if (dc == null) return ResponseEntity.notFound().build();
        // TODO: assertOwner(dc.getEventId());

        if (dto.getCode()!=null) dc.setCode(dto.getCode().trim().toUpperCase());
        if (dto.getType()!=null) dc.setType(dto.getType());
        if (dto.getValue()!=null) dc.setValue(dto.getValue());
        dc.setAppliesToTicketTierId(dto.getAppliesToTicketTierId());
        dc.setMaxUses(dto.getMaxUses());
        dc.setMinQty(dto.getMinQty());
        dc.setMaxQty(dto.getMaxQty());
        dc.setStartsAt(dto.getStartsAt());
        dc.setEndsAt(dto.getEndsAt());
        if (dto.getActive()!=null) dc.setActive(dto.getActive());
        repo.save(dc);
        return ResponseEntity.ok(dc);
    }

    // Delete (owner only)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var dc = repo.findById(id).orElse(null);
        if (dc == null) return ResponseEntity.notFound().build();
        // TODO: assertOwner(dc.getEventId());
        repo.delete(dc);
        return ResponseEntity.noContent().build();
    }
}
