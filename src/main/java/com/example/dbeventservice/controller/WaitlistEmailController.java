package com.example.dbeventservice.controller;

import com.example.dbeventservice.model.WaitlistEmail;
import com.example.dbeventservice.repositories.WaitlistEmailRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/waitlist")
@CrossOrigin(origins = "https://wkndevent.com") // 🔐 restrict in prod
public class WaitlistEmailController {

    private final WaitlistEmailRepository repo;

    // In-memory rate limiter map: IP -> lastSubmitTimestamp
    private final Map<String, LocalDateTime> lastSubmissionByIp = new ConcurrentHashMap<>();

    public WaitlistEmailController(WaitlistEmailRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<?> saveEmail(@RequestBody WaitlistEmail waitlistEmail, HttpServletRequest request) {
        String email = waitlistEmail.getEmail();
        String ip = request.getRemoteAddr();

        // 1. Validate email format
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return ResponseEntity.badRequest().body("Invalid email format.");
        }

        // 2. Basic in-memory rate limiting: 1 per minute per IP
        LocalDateTime lastSubmit = lastSubmissionByIp.get(ip);
        if (lastSubmit != null && lastSubmit.plusSeconds(60).isAfter(LocalDateTime.now())) {
            return ResponseEntity.status(429).body("Too many requests. Please wait before submitting again.");
        }

        lastSubmissionByIp.put(ip, LocalDateTime.now());

        // 3. Save if valid and not duplicate
        waitlistEmail.setSubmittedAt(LocalDateTime.now().toString());
        try {
            WaitlistEmail saved = repo.save(waitlistEmail);
            return ResponseEntity.ok(Map.of("message", "Email saved successfully"));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body("This email is already on the waitlist.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Something went wrong. Please try again later.");
        }
    }
}
