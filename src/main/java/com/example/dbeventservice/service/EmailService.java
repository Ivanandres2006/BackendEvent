package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.TicketEmailItem;
import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.util.QRCodeGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        // 1) Build the email first as multipart/alternative
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                msg,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, // <-- important
                StandardCharsets.UTF_8.name()
        );
        helper.setTo(to);
        helper.setSubject("Your WKND Tickets for " + event.getTitle());
        helper.setFrom("no-reply@wkndevent.com");

        // 2) Build Thymeleaf model, generate QR PNGs, and attach them inline
        List<Map<String, Object>> items = new ArrayList<>(tickets.size());
        for (Ticket t : tickets) {
            String cid = "qr-" + t.getId();                 // stable Content-ID per ticket
            String qrText = "TICKET:" + t.getId() + ":" + t.getOwner().getEmail();
            byte[] png = null;
            try {
                png = QRCodeGenerator.generatePngBytes(qrText, 300);
                helper.addInline(cid, new ByteArrayResource(png), "image/png");
            } catch (Exception e) {
                // leave without inline image; template will fall back to URL
            }

            // also include a public URL as a safe fallback
            String qrUrl = "https://backendevent-etce.onrender.com/api/tickets/qr/public/" + t.getId();

            Map<String, Object> model = new HashMap<>();
            model.put("id", t.getId());
            model.put("qrCid", cid);      // for <img src="cid:...">
            model.put("qrUrl", qrUrl);    // fallback <img src="https://...">
            items.add(model);
        }

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("event", event);
        ctx.setVariable("tickets", items);
        String html = templateEngine.process("ticket-email", ctx);

        // Plain-text alternative (already in your code; keep it)
        StringBuilder text = new StringBuilder();
        text.append("Hi ").append(name).append("\n\n")
                .append("Thanks for your purchase! 🎉\n")
                .append("Here are your tickets for ").append(event.getTitle()).append(":\n\n");
        for (Ticket t : tickets) {
            text.append("Ticket #").append(t.getId()).append("\n")
                    .append("Event: ").append(event.getTitle()).append("\n")
                    .append("Date: ").append(event.getDateTime()).append("\n")
                    .append("Location: ").append(event.getLocation()).append("\n")
                    .append("QR (if images blocked): https://backendevent-etce.onrender.com/api/tickets/qr/public/")
                    .append(t.getId()).append("\n")
                    .append("Apple Wallet (iPhone): https://backendevent-etce.onrender.com/api/passes/ticket/")
                    .append(t.getId()).append("\n\n");
        }
        text.append("— The WKND Team\n");

        helper.setText(text.toString(), html);
        mailSender.send(msg);
    }
}
