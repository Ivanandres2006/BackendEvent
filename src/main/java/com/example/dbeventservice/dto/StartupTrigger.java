package com.example.dbeventservice.dto;

import com.example.dbeventservice.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class StartupTrigger {

    private final UserRepository userRepository;

    public StartupTrigger(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        System.out.println("✅ Forcing JPA/Hibernate to initialize...");
        userRepository.count(); // 🧠 Any JPA method works here
    }
}

