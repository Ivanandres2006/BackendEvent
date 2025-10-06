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

    private final double fallbackRate;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern USD_PATTERN =
            Pattern.compile("USD\\s*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);

    private final AtomicReference<Double> cache = new AtomicReference<>(null);
    private volatile Instant fetchedAt = Instant.EPOCH;
    private volatile String lastSource = ""; // "override" | "bcv" | "cache" | "fallback"

    // ---- admin override state ----
    private final AtomicReference<Double> overrideRate = new AtomicReference<>(null);
    private volatile Instant overrideUntil = null;

    public BcvRateService(@Value("${bcv.fallback:179.43}") double fallbackRate) {
        this.fallbackRate = fallbackRate;
    }

    // Use Duration (matches AdminController)
    public void setOverride(double rate, Duration ttl) {
        overrideRate.set(rate);
        overrideUntil = (ttl == null || ttl.isZero() || ttl.isNegative())
                ? null : Instant.now().plus(ttl);
    }

    // Keep an Integer version too (if other callers still use it)
    public void setOverride(double rate, Integer ttlMinutes) {
        setOverride(rate, (ttlMinutes == null ? null : Duration.ofMinutes(ttlMinutes)));
    }

    public void clearOverride() {
        overrideRate.set(null);
        overrideUntil = null;
    }

    public boolean isOverrideActive() {
        Double r = overrideRate.get();
        if (r == null) return false;
        if (overrideUntil == null) return true;
        return Instant.now().isBefore(overrideUntil);
    }

    public Instant getOverrideUntil() { return overrideUntil; }
    public Double  getOverrideRate()  { return overrideRate.get(); }

    public Optional<Double> getVesPerUsd() {
        // 1) admin override takes precedence
        if (isOverrideActive()) {
            lastSource = "override";
            fetchedAt = Instant.now();
            return Optional.of(overrideRate.get());
        }

        // 2) fresh cache?
        if (cache.get() != null && Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0) {
            lastSource = "cache";
            return Optional.of(cache.get());
        }

        // 3) scrape BCV
        try {
            var req = HttpRequest.newBuilder(URI.create(BCV_URL))
                    .header("User-Agent", "WKND-FX/1.0")
                    .GET().build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                var m = USD_PATTERN.matcher(res.body());
                if (m.find()) {
                    var raw = m.group(1).trim();                // e.g. 179,43080000
                    var normalized = raw.replace(".", "").replace(",", ".");
                    double rate = Double.parseDouble(normalized);
                    cache.set(rate);
                    fetchedAt = Instant.now();
                    lastSource = "bcv";
                    return Optional.of(rate);
                }
            }
        } catch (Exception ignored) {}

        // 4) fallback
        if (cache.get() != null) {
            lastSource = "cache";
            return Optional.of(cache.get());
        }
        lastSource = "fallback";
        fetchedAt = Instant.now();
        return Optional.of(fallbackRate);
    }

    public Instant getFetchedAt() { return fetchedAt; }
    public String  getLastSource() { return lastSource; }
    public double  getFallbackRate() { return fallbackRate; }
}
