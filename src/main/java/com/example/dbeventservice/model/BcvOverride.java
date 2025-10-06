package com.example.dbeventservice.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "bcv_override")
public class BcvOverride {
    @Id
    private String id; // constant "fx"
    @Column(nullable = false)
    private double rate;
    @Column(name="override_until")
    private Instant overrideUntil;
    @Column(name="updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public BcvOverride() {}
    public BcvOverride(String id, double rate, Instant until) {
        this.id = id; this.rate = rate; this.overrideUntil = until; this.updatedAt = Instant.now();
    }
    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }
    public Instant getOverrideUntil() { return overrideUntil; }
    public void setOverrideUntil(Instant overrideUntil) { this.overrideUntil = overrideUntil; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
