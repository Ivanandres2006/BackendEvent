package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.TicketEmailItem;
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
        // Build a simple model list with id + inline Base64 QR for Thymeleaf
        List<Map<String, Object>> items = new ArrayList<>(tickets.size());
        for (Ticket t : tickets) {
            String base64 = null;
            try {
                String qrText = "TICKET:" + t.getId() + ":" + t.getOwner().getEmail();
                byte[] png = QRCodeGenerator.generatePngBytes(qrText, 300);
                base64 = Base64.getEncoder().encodeToString(png);
            } catch (Exception ignored) {}
            items.add(Map.of(
                    "id", t.getId(),
                    "qrBase64", base64
            ));
        }

        // Thymeleaf context
        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("event", event);
        ctx.setVariable("tickets", items);

        String html = templateEngine.process("ticket-email", ctx);

        // Plain-text fallback (good for deliverability + text-only clients)
        StringBuilder text = new StringBuilder();
        text.append("Hi ").append(name).append("\n\n")
                .append("Thanks for your purchase! 🎉\n")
                .append("Here are your tickets for ").append(event.getTitle()).append(":\n\n");
        for (Ticket t : tickets) {
            text.append("Ticket #").append(t.getId()).append("\n")
                    .append("Event: ").append(event.getTitle()).append("\n")
                    .append("Date: ").append(event.getDateTime()).append("\n")
                    .append("Location: ").append(event.getLocation()).append("\n")
                    .append("QR (open if HTML blocked): https://backendevent-etce.onrender.com/api/tickets/qr/public/")
                    .append(t.getId()).append("\n")
                    .append("Apple Wallet (iPhone): https://backendevent-etce.onrender.com/api/passes/ticket/")
                    .append(t.getId()).append("\n\n");
        }
        text.append("— The WKND Team\n");

        // Send multipart/alternative (plain + HTML)
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject("Your WKND Tickets for " + event.getTitle());
        helper.setFrom("no-reply@wkndevent.com"); // ensure this domain is verified with your email provider
        helper.setText(text.toString(), html);

        mailSender.send(msg);
    }
}
