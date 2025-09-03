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
        // 1) Prepare message in MIXED_RELATED so HTML + inline images live together
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                msg,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name()
        );
        helper.setTo(to);
        helper.setFrom("no-reply@wkndevent.com");
        helper.setSubject("Your WKND Tickets for " + event.getTitle());

        // 2) Build model for Thymeleaf AND collect inline image payloads to attach after we set HTML
        record InlineImg(String cid, byte[] data, String contentType) {}
        List<InlineImg> inlineImages = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>(tickets.size());

        for (Ticket t : tickets) {
            String cid = "qr-" + t.getId(); // must match 'cid:' + ${ticket.qrCid} in template

            // Build what you encode in the QR (keep your existing format)
            String qrText = "TICKET:" + t.getId() + ":" + t.getOwner().getEmail();

            try {
                byte[] png = QRCodeGenerator.generatePngBytes(qrText, 300);
                inlineImages.add(new InlineImg(cid, png, "image/png"));
            } catch (Exception e) {
                // If QR generation fails, we'll rely on qrUrl fallback in HTML
            }

            String qrUrl = "https://backendevent-etce.onrender.com/api/tickets/qr/public/" + t.getId();

            Map<String, Object> model = new HashMap<>();
            model.put("id", t.getId());
            model.put("qrCid", cid);   // primary: <img src="cid:qr-...">
            model.put("qrUrl", qrUrl); // fallback remote PNG
            items.add(model);
        }

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("event", event);
        ctx.setVariable("tickets", items);

        String html = templateEngine.process("ticket-email", ctx);

        // 3) Set HTML body (IMPORTANT: use the boolean 'html' overload for reliable inline resolution)
        helper.setText(html, true);

        // 4) Attach inline images AFTER setting HTML (safer across mail clients)
        for (InlineImg img : inlineImages) {
            helper.addInline(img.cid(), new ByteArrayResource(img.data()), img.contentType());
        }

        // (Optional) If you want a text-only alternative, send a separate plain email, or
        // switch to a custom MimeStructure. Many clients ignore text/alt when using related inlines.

        // 5) Send
        mailSender.send(msg);
    }
}
