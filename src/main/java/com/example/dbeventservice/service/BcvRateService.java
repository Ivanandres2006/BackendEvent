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

    private static final String BCV_URL = "https://www.bcv.org.ve/";
    private static final Duration TTL = Duration.ofMinutes(30);

    // Tolerant pattern: accepts “USD 179,43080000” or similar
    private static final Pattern USD_PATTERN = Pattern.compile(
            "(?:USD|D[óo]lar\\s+estadounidense)\\s*([0-9][0-9\\.,]{2,})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Configurable fallback (default 179.2)
    private final double fallbackRate;

    private final AtomicReference<Double> lastGood = new AtomicReference<>(null);
    private volatile Instant fetchedAt = Instant.EPOCH;
    private volatile String lastSource = "none"; // "bcv" | "cache" | "fallback"

    public BcvRateService(@Value("${fx.vesPerUsd.fallback:179.2}") double fallbackRate) {
        this.fallbackRate = fallbackRate;
    }

    /** Return fresh if possible; else cache; else empty (controller handles fallback). */
    public Optional<Double> getVesPerUsd() {
        // Fresh cache
        Double cached = lastGood.get();
        if (cached != null && Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0) {
            lastSource = "cache";
            return Optional.of(cached);
        }

        // Try BCV
        try {
            var req = HttpRequest.newBuilder(URI.create(BCV_URL))
                    .header("User-Agent", "WKND-FX/1.0")
                    .GET()
                    .build();

            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                Matcher m = USD_PATTERN.matcher(res.body());
                if (m.find()) {
                    // Typical BCV uses comma for decimals; remove thousand separators
                    String raw = m.group(1).trim();               // e.g. 179,43080000
                    String normalized = raw.replace(".", "")      // remove thousand dots
                            .replace(",", ".");     // use dot as decimal
                    double rate = Double.parseDouble(normalized);

                    lastGood.set(rate);
                    fetchedAt = Instant.now();
                    lastSource = "bcv";
                    return Optional.of(rate);
                }
            }
        } catch (Exception ignored) { }

        // Stale cache still better than nothing
        if (lastGood.get() != null) {
            lastSource = "cache";
            return Optional.of(lastGood.get());
        }

        // Controller will use fallback
        return Optional.empty();
    }

    public double getFallbackRate() { return fallbackRate; }
    public Instant getFetchedAt() { return fetchedAt.equals(Instant.EPOCH) ? Instant.now() : fetchedAt; }
    public String getLastSource() { return lastSource; }
}
