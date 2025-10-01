package com.example.dbeventservice.controller;

import com.example.dbeventservice.service.BcvRateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fx")
public class FxController {

    private final BcvRateService bcv;

    public FxController(BcvRateService bcv) {
        this.bcv = bcv;
    }

    @GetMapping("/ves-per-usd")
    public ResponseEntity<?> vesPerUsd() {
        var opt = bcv.getVesPerUsd();

        double rate = opt.orElse(bcv.getFallbackRate());
        String source = opt.isPresent() ? bcv.getLastSource() : "fallback";

        return ResponseEntity.ok(
                Map.of(
                        "source", source,
                        "vesPerUsd", rate,
                        "fetchedAt", bcv.getFetchedAt().toString()
                )
        );
    }
}
