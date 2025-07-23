package com.example.dbeventservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(Environment env) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        String host = env.getProperty("spring.mail.host");
        String user = env.getProperty("spring.mail.username");

        System.out.println("📨 Mail sender config:");
        System.out.println("Host: " + host);
        System.out.println("User: " + user);

        mailSender.setHost(host);
        mailSender.setPort(Integer.parseInt(env.getProperty("spring.mail.port", "587")));
        mailSender.setUsername(user);
        mailSender.setPassword(env.getProperty("spring.mail.password"));

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");

        return mailSender;
    }
}

