package com.example.dbeventservice.dto;

import com.example.dbeventservice.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupTrigger {

    private final UserRepository userRepository;

    public StartupTrigger(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        System.out.println("✅ Forcing JPA/Hibernate to initialize...");
        //userRepository.count(); // 🧠 This is safe now
    }
}

