package com.example.dbeventservice.service;

import com.example.dbeventservice.model.BcvOverride;
import com.example.dbeventservice.repositories.BcvOverrideRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class BcvRateService {
    private static final String BCV_URL = "https://www.bcv.org.ve/";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Pattern USD_PATTERN = Pattern.compile("USD\\s*([0-9\\.,]+)", Pattern.CASE_INSENSITIVE);

    private final double fallbackRate;
    private final BcvOverrideRepository repo;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

    // in-memory cache of last fetched BCV
    private final AtomicReference<Double> cache = new AtomicReference<>(null);
    private volatile Instant fetchedAt = Instant.EPOCH;
    private volatile String lastSource = ""; // "override" | "bcv" | "cache" | "fallback"

    // override state (mirrors DB)
    private final AtomicReference<Double> overrideRate = new AtomicReference<>(null);
    private volatile Instant overrideUntil = null;

    public BcvRateService(@Value("${bcv.fallback:179.43}") double fallbackRate,
                          BcvOverrideRepository repo) {
        this.fallbackRate = fallbackRate;
        this.repo = repo;
    }

    /** Load persisted override on boot */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOverrideFromDb() {
        repo.findById("fx").ifPresent(row -> {
            overrideRate.set(row.getRate());
            overrideUntil = row.getOverrideUntil();
        });
    }

    // ---- admin API ----
    public void setOverride(double rate, Duration ttl) {
        overrideRate.set(rate);
        overrideUntil = (ttl == null || ttl.isZero() || ttl.isNegative()) ? null : Instant.now().plus(ttl);
        repo.save(new BcvOverride("fx", rate, overrideUntil));       // persist
        lastSource = "override";                                     // immediate reporting
        fetchedAt  = Instant.now();
    }
    public void setOverride(double rate, Integer ttlMinutes) { setOverride(rate, ttlMinutes==null?null:Duration.ofMinutes(ttlMinutes)); }
    public void clearOverride() {
        overrideRate.set(null); overrideUntil = null;
        repo.deleteById("fx");                                       // remove persisted row
    }

    public boolean isOverrideActive() {
        Double r = overrideRate.get();
        if (r == null) return false;
        return overrideUntil == null || Instant.now().isBefore(overrideUntil);
    }
    public Instant getOverrideUntil() { return overrideUntil; }
    public Double  getOverrideRate()  { return overrideRate.get(); }

    // ---- public read ----
    public Optional<Double> getVesPerUsd() {
        // 1) override (persisted)
        if (isOverrideActive()) {
            lastSource = "override"; fetchedAt = Instant.now();
            return Optional.of(overrideRate.get());
        } else if (overrideRate.get() != null) {
            // expired → clear persisted row once
            clearOverride();
        }

        // 2) fresh cache
        if (cache.get() != null && Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0) {
            lastSource = "cache"; return Optional.of(cache.get());
        }

        // 3) scrape BCV
        try {
            var res = http.send(HttpRequest.newBuilder(URI.create(BCV_URL))
                    .header("User-Agent","WKND-FX/1.0").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                var m = USD_PATTERN.matcher(res.body());
                if (m.find()) {
                    var raw = m.group(1).trim();                // e.g. 179,43080000
                    var normalized = raw.replace(".", "").replace(",", ".");
                    double rate = Double.parseDouble(normalized);
                    cache.set(rate); fetchedAt = Instant.now(); lastSource = "bcv";
                    return Optional.of(rate);
                }
            }
        } catch (Exception ignored) {}

        // 4) fallback (or stale cache)
        if (cache.get() != null) { lastSource = "cache"; return Optional.of(cache.get()); }
        lastSource = "fallback"; fetchedAt = Instant.now();
        return Optional.of(fallbackRate);
    }

    public Instant getFetchedAt()   { return fetchedAt; }
    public String  getLastSource()  { return lastSource; }
    public double  getFallbackRate(){ return fallbackRate; }
}
