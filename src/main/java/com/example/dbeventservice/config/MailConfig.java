package com.example.dbeventservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${app.mail.envelopeFrom:bounces@wkndevent.com}") // SMTP MAIL FROM (Return-Path)
    private String envelopeFrom;

    @Value("${app.mail.localhost:wkndevent.com}")            // EHLO/HELO name
    private String smtpLocalhost;

    @Value("${app.mail.sslTrust:smtp.mailgun.org}")          // host to trust (Mailgun default)
    private String sslTrust;

    @Value("${app.mail.debug:false}")                        // enable temporarily if you need logs
    private boolean debug;

    @Bean
    public JavaMailSender javaMailSender(Environment env) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(env.getProperty("spring.mail.host"));
        sender.setPort(Integer.parseInt(env.getProperty("spring.mail.port", "587")));
        sender.setUsername(env.getProperty("spring.mail.username"));
        sender.setPassword(env.getProperty("spring.mail.password"));
        sender.setDefaultEncoding("UTF-8");

        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.starttls.required", "true");

        // sensible timeouts
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");

        // deliverability / infra hygiene
        p.put("mail.smtp.from", envelopeFrom); // envelope sender = Return-Path
        p.put("mail.smtp.localhost", smtpLocalhost);

        if (sslTrust != null && !sslTrust.isBlank()) {
            p.put("mail.smtp.ssl.trust", sslTrust);
        }

        p.put("mail.debug", Boolean.toString(debug));
        return sender;
    }
}
