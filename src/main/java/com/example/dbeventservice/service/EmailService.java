package com.example.dbeventservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void sendVerificationEmail(String to, String name, String code) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        // Prepare template context
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("code", code);
        String htmlContent = templateEngine.process("verification-email", context);

        // Configure email
        helper.setTo(to);
        helper.setSubject("Verify your email");
        helper.setFrom("no-reply@dbevent.com");
        helper.setText(htmlContent, true); // true = HTML

        mailSender.send(mimeMessage);
    }

    public void sendPasswordResetEmail(String to, String name, String code) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("code", code);

        String htmlContent = templateEngine.process("reset-password-email", context); // NEW TEMPLATE

        helper.setTo(to);
        helper.setSubject("Reset your password");
        helper.setFrom("no-reply@dbevent.com");
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
    }
}
