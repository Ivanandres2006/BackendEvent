package com.example.dbeventservice.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

@Component
public class ContactHashUtil {
    // Optional pepper from properties (inject via @Value if you want)
    private static final String PEPPER = "change-me-in-prod";

    public String hashEmail(String email) {
        var norm = normalizeEmail(email);
        return sha256Hex(norm + PEPPER);
    }

    public String hashPhone(String phone) {
        var norm = normalizePhone(phone);
        return sha256Hex(norm + PEPPER);
    }

    public String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizePhone(String phone) {
        if (phone == null) return "";
        // keep digits only; your SMS provider may require E.164 — adapt if needed.
        return phone.replaceAll("\\D+", "");
    }

    private String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
