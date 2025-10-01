package com.example.dbeventservice.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BcvRateService {
    private static final String BCV_URL = "https://www.bcv.org.ve/";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final AtomicReference<Double> cached = new AtomicReference<>(null);
    private volatile Instant fetchedAt = Instant.EPOCH;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Matches "... USD 179,43080000" (comma decimal), also tolerant to spaces/newlines
    private static final Pattern USD_PATTERN =
            Pattern.compile("USD\\s*([0-9\\.\\,]+)", Pattern.CASE_INSENSITIVE);

    public Optional<Double> getVesPerUsd() {
        if (Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0 && cached.get() != null) {
            return Optional.of(cached.get());
        }
        try {
            var req = HttpRequest.newBuilder(URI.create(BCV_URL))
                    .header("User-Agent", "WKND-FX/1.0")
                    .GET()
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                var m = USD_PATTERN.matcher(res.body());
                if (m.find()) {
                    var raw = m.group(1).trim();       // e.g. "179,43080000" or "179.43080000"
                    // Normalize VE-style: remove thousands '.', use ',' as decimal
                    var normalized = raw.replace(".", "").replace(",", ".");
                    double rate = Double.parseDouble(normalized);
                    cached.set(rate);
                    fetchedAt = Instant.now();
                    return Optional.of(rate);
                }
            }
        } catch (Exception ignored) {}
        return Optional.ofNullable(cached.get()); // return last good value if present
    }

    public Instant getFetchedAt() { return fetchedAt; }
}

