package com.example.dbeventservice.controller;
import com.example.dbeventservice.service.BcvRateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/fx")
public class FxController {
    private final BcvRateService bcv;
    public FxController(BcvRateService bcv) { this.bcv = bcv; }

    @GetMapping("/ves-per-usd")
    public ResponseEntity<?> vesPerUsd() {
        return bcv.getVesPerUsd()
                .<ResponseEntity<?>>map(rate -> ResponseEntity.ok(
                        Map.of("source", "bcv", "vesPerUsd", rate, "fetchedAt", bcv.getFetchedAt().toString())
                ))
                .orElseGet(() -> ResponseEntity.status(503).body(Map.of("error", "bcv_unavailable")));
    }
}
