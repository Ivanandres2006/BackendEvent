package com.example.dbeventservice.controller;

import com.example.dbeventservice.model.WaitlistEmail;
import com.example.dbeventservice.repositories.WaitlistEmailRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/waitlist")
@CrossOrigin(origins = "*") // 🔥 allow Vercel frontend
public class WaitlistEmailController {

    private final WaitlistEmailRepository repo;

    public WaitlistEmailController(WaitlistEmailRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public WaitlistEmail saveEmail(@RequestBody WaitlistEmail waitlistEmail) {
        waitlistEmail.setSubmittedAt(LocalDateTime.now().toString());
        return repo.save(waitlistEmail);
    }
}
