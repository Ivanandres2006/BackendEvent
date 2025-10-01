package com.example.dbeventservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class BcvRateService {
    private static final String BCV_URL = "https://www.bcv.org.ve/";
    private static final Duration TTL = Duration.ofMinutes(30);

    // Default fallback can be overridden via env/props: bcv.fallback
    private final double fallbackRate;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Matches e.g. "... USD 179,43080000"
    private static final Pattern USD_PATTERN = Pattern.compile(
            "USD\\s*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE
    );

    private final AtomicReference<Double> cache = new AtomicReference<>(null);
    private volatile Instant fetchedAt = Instant.EPOCH;
    private volatile String lastSource = ""; // "bcv" | "cache" | "fallback"

    public BcvRateService(@Value("${bcv.fallback:179.43}") double fallbackRate) {
        this.fallbackRate = fallbackRate;
    }

    public Optional<Double> getVesPerUsd() {
        // If cache is fresh, serve it
        if (cache.get() != null && Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0) {
            lastSource = "cache";
            return Optional.of(cache.get());
        }

        // Try live BCV
        try {
            var req = HttpRequest.newBuilder(URI.create(BCV_URL))
                    .header("User-Agent", "WKND-FX/1.0")
                    .GET()
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                var m = USD_PATTERN.matcher(res.body());
                if (m.find()) {
                    var raw = m.group(1).trim(); // e.g. 179,43080000
                    var normalized = raw.replace(".", "").replace(",", ".");
                    double rate = Double.parseDouble(normalized);
                    cache.set(rate);
                    fetchedAt = Instant.now();
                    lastSource = "bcv";
                    return Optional.of(rate);
                }
            }
        } catch (Exception ignored) {
            // fall through
        }

        // Fall back to existing cache or hard fallback
        if (cache.get() != null) {
            lastSource = "cache";
            return Optional.of(cache.get());
        }
        lastSource = "fallback";
        fetchedAt = Instant.now();
        return Optional.of(fallbackRate);
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    /** Expose which source was used for the last successful call: "bcv" | "cache" | "fallback". */
    public String getLastSource() {
        return lastSource;
    }

    /** Expose the configured fallback so controller can include it in the response if desired. */
    public double getFallbackRate() {
        return fallbackRate;
    }
}
