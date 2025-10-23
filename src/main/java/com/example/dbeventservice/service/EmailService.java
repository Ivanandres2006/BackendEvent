package com.example.dbeventservice.service;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.util.QRCodeGenerator;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailService implements Mailer {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from.address:no-reply@wkndevent.com}")
    private String fromAddress;

    @Value("${app.mail.from.name:WKND}")
    private String fromName;

    @Value("${app.mail.replyTo:support@wkndevent.com}")
    private String replyTo;

    @Value("${app.mail.messageIdDomain:wkndevent.com}")
    private String messageIdDomain;

    @Value("${app.mail.unsubscribeUrl:https://www.wkndevent.com/unsubscribe}")
    private String unsubscribeUrl;

    private byte[] appleWalletBadgePng; // optional

    @jakarta.annotation.PostConstruct
    void loadWalletBadge() {
        try (InputStream in = new ClassPathResource("static/img/add-to-apple-wallet.png").getInputStream()) {
            appleWalletBadgePng = in.readAllBytes();
        } catch (Exception ignored) {
            appleWalletBadgePng = null;
        }
    }

    // =========================
    // Public API
    // =========================

    @Override
    public void sendVerificationEmail(String to, String name, String code) throws MessagingException {
        // Template must include th:inline="text" so [[${...}]] renders server-side
        String html = render("verification-email", Map.of("name", name, "code", code));
        // HTML-only to keep styles (avoid clients selecting text/plain)
        sendHtmlOnly(to, "Your WKND verification code", html);
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String code) throws MessagingException {
        String html = render("reset-password-email", Map.of("name", name, "code", code));
        sendHtmlOnly(to, "WKND password reset code", html);
    }

    @Override
    public void sendTicketEmail(String to, String name, Event event, List<Ticket> tickets) throws MessagingException {
        record InlineImg(String cid, byte[] bytes, String type) {}
        List<InlineImg> inlines = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>(tickets.size());

        for (Ticket t : tickets) {
            String cid = "qr-" + t.getId();
            String qrText = "TICKET:" + t.getId() + ":" + t.getOwner().getEmail();
            try {
                byte[] png = QRCodeGenerator.generatePngBytes(qrText, 300);
                inlines.add(new InlineImg(cid, png, "image/png"));
            } catch (Exception ignored) {}
            String qrUrl = "https://backendevent-etce.onrender.com/api/tickets/qr/public/" + t.getId();
            items.add(Map.of("id", t.getId(), "qrCid", cid, "qrUrl", qrUrl));
        }

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("event", event);
        ctx.setVariable("tickets", items);
        String html = templateEngine.process("ticket-email", ctx);

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(
                msg, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name()
        );
        setStandardHeaders(h, to, "Your WKND Tickets for " + event.getTitle());
        h.setText(plainTicketsText(name, event, tickets), html);

        if (appleWalletBadgePng != null) {
            h.addInline("wallet-badge", new ByteArrayResource(appleWalletBadgePng), "image/png");
        }
        for (InlineImg i : inlines) {
            h.addInline(i.cid(), new ByteArrayResource(i.bytes()), i.type());
        }
        mailSender.send(msg);
    }

    // =========================
    // Internals
    // =========================

    private String render(String tpl, Map<String, Object> model) {
        Context c = new Context();
        model.forEach(c::setVariable);
        return templateEngine.process(tpl, c);
    }

    /** HTML-only for short transactional emails (prevents “unstyled” rendering in some clients). */
    private void sendHtmlOnly(String to, String subject, String html) throws MessagingException {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, StandardCharsets.UTF_8.name());
        setStandardHeaders(h, to, subject);
        h.setText(html, true); // HTML content
        mailSender.send(msg);
    }

    private void setStandardHeaders(MimeMessageHelper h, String to, String subject) throws MessagingException {
        try {
            h.setTo(new InternetAddress(to));
            h.setFrom(new InternetAddress(fromAddress, fromName));
        } catch (Exception e) {
            h.setFrom(fromAddress);
        }
        h.setReplyTo(replyTo);
        h.setSubject(subject);

        var m = h.getMimeMessage();
        m.setHeader("Auto-Submitted", "auto-generated");
        m.setHeader("X-Auto-Response-Suppress", "All");
        m.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">, <mailto:unsubscribe@" + messageIdDomain + ">");
        m.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        m.setHeader("Message-ID", "<" + UUID.randomUUID() + "." + System.currentTimeMillis() + "@" + messageIdDomain + ">");
    }

    private static String plainTicketsText(String name, Event event, List<Ticket> tickets) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(safe(name)).append(",\n\n")
                .append("Here are your tickets for ").append(event.getTitle()).append(":\n");
        for (Ticket t : tickets) {
            sb.append("- Ticket #").append(t.getId()).append(" • QR: ")
                    .append("https://backendevent-etce.onrender.com/api/tickets/qr/public/")
                    .append(t.getId()).append("\n");
        }
        sb.append("\nEnjoy!\n— WKND\n");
        return sb.toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
