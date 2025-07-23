package com.example.dbeventservice.controller;

import com.example.dbeventservice.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestEmailController {

    private final JavaMailSender mailSender;

    public TestEmailController(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @GetMapping("/test-email")
    public ResponseEntity<String> sendTestEmail() {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setTo("madrizivanandres@gmail.com"); // 🔁 Put your email here
            helper.setSubject("Test Email from Spring Boot + Mailgun");
            helper.setText("This is a test email sent using Mailgun SMTP via Spring Boot.", true);
            helper.setFrom("postmaster@wkndevent.com");

            mailSender.send(message);
            return ResponseEntity.ok("✅ Email sent (or attempted)");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("❌ Error sending email: " + e.getMessage());
        }
    }
}


