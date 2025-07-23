package com.example.dbeventservice.controller;

import com.example.dbeventservice.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestEmailController {

    @Autowired
    private EmailService emailService;

    @GetMapping("/test-email")
    public String testEmail() {
        System.out.println("🧪 /test-email endpoint triggered");
        try {
            emailService.sendVerificationEmail("madrizivanandres@gmail.com", "Test User", "ABC123");
            System.out.println("✅ Email method finished");
            return "✅ Email sent (or attempted)";
        } catch (Exception e) {
            System.out.println("❌ ERROR during sendVerificationEmail");
            e.printStackTrace();
            return "❌ " + e.getMessage();
        }
    }
}

