package com.example.dbeventservice.service;


import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.*;

@Service
public class BcvRateService {
    private static final String BCV_URL = "https://www.bcv.org.ve/";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Matches "... USD 179,43080000"
    private static final Pattern USD_PATTERN = Pattern.compile(
            "USD\\s*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE
    );

    private final AtomicReference<Double> cache = new AtomicReference<>(null);
    private volatile Instant fetchedAt = Instant.EPOCH;

    public Optional<Double> getVesPerUsd() {
        if (cache.get() != null && Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0) {
            return Optional.of(cache.get());
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
                    var raw = m.group(1).trim();      // e.g. 179,43080000
                    var normalized = raw.replace(".", "").replace(",", ".");
                    double rate = Double.parseDouble(normalized);
                    cache.set(rate);
                    fetchedAt = Instant.now();
                    return Optional.of(rate);
                }
            }
        } catch (Exception ignored) {}
        return Optional.ofNullable(cache.get());
    }

    public Instant getFetchedAt() { return fetchedAt; }
}
