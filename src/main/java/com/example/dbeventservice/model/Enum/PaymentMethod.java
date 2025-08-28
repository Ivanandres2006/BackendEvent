package com.example.dbeventservice.model.Enum;

public enum PaymentMethod {
    card,       // Stripe card / Apple Pay
    zelle,
    pagoMovil,
    cash;

    public static PaymentMethod from(String raw) {
        if (raw == null) return card;
        try { return PaymentMethod.valueOf(raw.trim()); }
        catch (Exception e) { return card; }
    }
}
