package com.example.dbeventservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BcvRateService {
    // BCV sometimes caches aggressively—add a cache-buster.
    private static final String BCV_URL = "https://www.bcv.org.ve/?t=";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Matches “… USD 179,43080000” with or without extra spaces/newlines
    private static final Pattern USD_PATTERN = Pattern.compile(
            "USD\\s*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final AtomicReference<Double> cache = new AtomicReference<>(null);
    private volatile Instant fetchedAt = Instant.EPOCH;

    public Optional<Double> getVesPerUsd() {
        if (cache.get() != null && Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0) {
            return Optional.of(cache.get());
        }
        try {
            var req = HttpRequest.newBuilder(URI.create(BCV_URL + System.currentTimeMillis()))
                    .header("User-Agent", "WKND-FX/1.0")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "es-VE,es;q=0.9,en;q=0.8")
                    .header("Cache-Control", "no-cache")
                    .GET()
                    .build();

            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) {
                var m = USD_PATTERN.matcher(res.body());
                if (m.find()) {
                    String raw = m.group(1).trim();            // e.g. 179,43080000
                    String normalized = raw.replace(".", "").replace(",", ".");
                    double rate = Double.parseDouble(normalized);
                    cache.set(rate);
                    fetchedAt = Instant.now();
                    return Optional.of(rate);
                }
            }
        } catch (Exception ignored) { /* we’ll serve stale cache if any */ }
        return Optional.ofNullable(cache.get());
    }

    public Instant getFetchedAt() { return fetchedAt; }
}
