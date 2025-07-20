package com.example.dbeventservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class BeanDebugger implements CommandLineRunner {

    @Autowired
    private ApplicationContext context;

    @Override
    public void run(String... args) {
        System.out.println("📦 Registered beans containing 'mail':");
        for (String name : context.getBeanDefinitionNames()) {
            if (name.toLowerCase().contains("mail")) {
                System.out.println("🔍 " + name);
            }
        }
    }
}
