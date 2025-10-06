package com.example.dbeventservice.controller;

import com.example.dbeventservice.service.BcvRateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/fx")
public class FxController {
    private final BcvRateService bcv;
    public FxController(BcvRateService bcv) { this.bcv = bcv; }

    @GetMapping("/ves-per-usd")
    public ResponseEntity<?> vesPerUsd() {
        return bcv.getVesPerUsd()
                .<ResponseEntity<?>>map(rate -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("vesPerUsd", rate);
                    body.put("source", Optional.ofNullable(bcv.getLastSource()).orElse(""));
                    Instant fetched = bcv.getFetchedAt();
                    if (fetched != null) body.put("fetchedAt", fetched.toString());
                    body.put("fallback", bcv.getFallbackRate());
                    body.put("overrideActive", bcv.isOverrideActive());
                    Instant until = bcv.getOverrideUntil();
                    if (until != null) body.put("overrideUntil", until.toString());
                    Double or = bcv.getOverrideRate();
                    if (or != null) body.put("overrideRate", or);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(503)
                        .body(Collections.singletonMap("error", "bcv_unavailable")));
    }
}


