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
    private volatile String lastSource = ""; // "override" | "bcv" | "cache" | "fallback"

    // ---------- Admin override ----------
    private final AtomicReference<Double> overrideRate = new AtomicReference<>(null);
    private volatile Instant overrideUntil = Instant.EPOCH;

    public BcvRateService(@Value("${bcv.fallback:179.43}") double fallbackRate) {
        this.fallbackRate = fallbackRate;
    }

    public Optional<Double> getVesPerUsd() {
        // 0) If an override is active, it wins (and refreshes cache)
        if (isOverrideActive()) {
            var rate = overrideRate.get();
            cache.set(rate);
            fetchedAt = Instant.now();
            lastSource = "override";
            return Optional.of(rate);
        }

        // 1) If cache is fresh, serve it
        if (cache.get() != null && Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0) {
            lastSource = "cache";
            return Optional.of(cache.get());
        }

        // 2) Try live BCV
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

        // 3) Fall back to existing cache or hard fallback
        if (cache.get() != null) {
            lastSource = "cache";
            return Optional.of(cache.get());
        }
        lastSource = "fallback";
        fetchedAt = Instant.now();
        return Optional.of(fallbackRate);
    }

    public Instant getFetchedAt() { return fetchedAt; }
    public String getLastSource() { return lastSource; }
    public double getFallbackRate() { return fallbackRate; }

    // ---------- Override controls ----------
    public void setOverride(double rate, Duration ttl) {
        if (rate <= 0) throw new IllegalArgumentException("Rate must be > 0");
        overrideRate.set(rate);
        overrideUntil = Instant.now().plus(ttl != null ? ttl : Duration.ofHours(12));
        lastSource = "override";
        fetchedAt = Instant.now();
        cache.set(rate); // keep public GET fast/consistent
    }

    public void clearOverride() {
        overrideRate.set(null);
        overrideUntil = Instant.EPOCH;
    }

    public boolean isOverrideActive() {
        var r = overrideRate.get();
        return (r != null && r > 0 && Instant.now().isBefore(overrideUntil));
    }

    public Instant getOverrideUntil() { return overrideUntil; }
    public Double getOverrideRate() { return overrideRate.get(); }
}
