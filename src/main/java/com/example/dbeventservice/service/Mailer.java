// src/main/java/com/example/dbeventservice/service/Mailer.java
package com.example.dbeventservice.service;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Ticket;

import jakarta.mail.MessagingException;
import java.util.List;

public interface Mailer {
    void sendVerificationEmail(String to, String name, String code) throws MessagingException;
    void sendPasswordResetEmail(String to, String name, String code) throws MessagingException;
    void sendTicketEmail(String to, String name, Event event, List<Ticket> tickets) throws MessagingException;
}
