package com.example.dbeventservice.service;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.util.QRCodeGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    private String appleWalletBadgeBase64;

    // ✅ Load Apple Wallet badge image at startup
    @PostConstruct
    public void initBadge() throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(
                    new ClassPathResource("static/img/add-to-apple-wallet.png").getFile().toPath()
            );
            appleWalletBadgeBase64 = Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            System.err.println("⚠️ Could not load Apple Wallet badge: " + e.getMessage());
            appleWalletBadgeBase64 = null;
        }
    }

    public void sendVerificationEmail(String to, String name, String code) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("code", code);
        String htmlContent = templateEngine.process("verification-email", context);

        helper.setTo(to);
        helper.setSubject("Verify your email");
        helper.setFrom("no-reply@wkndevent.com");
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
    }

    public void sendPasswordResetEmail(String to, String name, String code) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("code", code);

        String htmlContent = templateEngine.process("reset-password-email", context);

        helper.setTo(to);
        helper.setSubject("Reset your password");
        helper.setFrom("no-reply@wkndevent.com");
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
    }

    public void sendTicketEmail(String to, String name, Event event, List<Ticket> tickets) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "utf-8");

        List<Map<String, Object>> ticketModels = new ArrayList<>();
        for (Ticket t : tickets) {
            Map<String, Object> model = new HashMap<>();
            model.put("id", t.getId());
            model.put("event", t.getEvent());

            try {
                String qrText = "TICKET:" + t.getId() + ":" + t.getOwner().getEmail();
                byte[] png = QRCodeGenerator.generatePngBytes(qrText, 300);
                String base64 = Base64.getEncoder().encodeToString(png);
                model.put("qrBase64", base64);
            } catch (Exception e) {
                model.put("qrBase64", null);
            }
            ticketModels.add(model);
        }

        // ✅ Prepare Thymeleaf context
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("event", event);
        context.setVariable("tickets", ticketModels);
        context.setVariable("appleWalletBadgeBase64", appleWalletBadgeBase64);

        String htmlContent = templateEngine.process("ticket-email", context);

        // ✅ Plain-text fallback
        StringBuilder textFallback = new StringBuilder();
        textFallback.append("Hi ").append(name).append(",\n\n")
                .append("Thanks for your purchase! 🎉\n")
                .append("Here are your tickets for ").append(event.getTitle()).append(":\n\n");

        for (Ticket t : tickets) {
            textFallback.append("Ticket #").append(t.getId()).append("\n")
                    .append("Event: ").append(event.getTitle()).append("\n")
                    .append("Date: ").append(event.getDateTime()).append("\n")
                    .append("Location: ").append(event.getLocation()).append("\n")
                    .append("QR: https://backendevent-etce.onrender.com/api/tickets/qr/public/")
                    .append(t.getId()).append("\n")
                    .append("Apple Wallet (iPhone only): https://backendevent-etce.onrender.com/api/passes/ticket/")
                    .append(t.getId()).append("\n\n");
        }

        textFallback.append("Enjoy your night out 🥳\n")
                .append("— The WKND Team\n");

        helper.setTo(to);
        helper.setSubject("Your WKND Tickets for " + event.getTitle());
        helper.setFrom("no-reply@wkndevent.com");
        helper.setText(textFallback.toString(), htmlContent);

        mailSender.send(mimeMessage);
    }
}
