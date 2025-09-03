package com.example.dbeventservice.service;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.util.QRCodeGenerator;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;

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
        helper.setFrom("no-replay@wkndevent.com");
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
        helper.setFrom("no-replay@wkndevent.com"); // ✅ Match your Mailgun domain
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
    }

    public void sendTicketEmail(String to, String name, Event event, List<Ticket> tickets) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "utf-8");

        // Build list of models (ticket info + inline QR base64)
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

        // Prepare template context
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("event", event);
        context.setVariable("tickets", ticketModels);

        // Render HTML
        String htmlContent = templateEngine.process("ticket-email", context);

        // Build plain-text fallback
        StringBuilder textFallback = new StringBuilder();
        textFallback.append("Hi ").append(name).append(",\n\n")
                .append("Thanks for your purchase! 🎉\n")
                .append("Here are your tickets for ").append(event.getTitle()).append(":\n\n");

        for (Ticket t : tickets) {
            textFallback.append("Ticket #").append(t.getId()).append("\n")
                    .append("Event: ").append(event.getTitle()).append("\n")
                    .append("Date: ").append(event.getDateTime()).append("\n")
                    .append("Location: ").append(event.getLocation()).append("\n")
                    .append("Show this QR at the entrance:\n")
                    .append("https://backendevent-etce.onrender.com/api/tickets/qr/public/")
                    .append(t.getId()).append("\n\n");
        }

        textFallback.append("Enjoy your night out 🥳\n")
                .append("— The WKND Team\n");

        // Set email contents
        helper.setTo(to);
        helper.setSubject("Your WKND Tickets for " + event.getTitle());
        helper.setFrom("no-reply@wkndevent.com");

        // First arg = plain-text, second arg = HTML
        helper.setText(textFallback.toString(), htmlContent);

        mailSender.send(mimeMessage);
    }


}
