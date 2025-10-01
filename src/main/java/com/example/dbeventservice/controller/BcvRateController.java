package com.example.dbeventservice.controller;

import com.example.dbeventservice.service.BcvRateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/fx")
public class BcvRateController {

    private final BcvRateService bcv;

    public BcvRateController(BcvRateService bcv) { this.bcv = bcv; }

    @GetMapping("/ves-per-usd")
    public ResponseEntity<?> vesPerUsd() {
        var rateOpt = bcv.getVesPerUsd();
        if (rateOpt.isEmpty()) return ResponseEntity.status(503).body(Map.of("error", "bcv_unavailable"));
        double rate = rateOpt.get();
        Instant ts = bcv.getFetchedAt();
        return ResponseEntity.ok(Map.of(
                "source", "bcv",
                "vesPerUsd", rate,
                "fetchedAt", ts.toString()
        ));
    }
}

