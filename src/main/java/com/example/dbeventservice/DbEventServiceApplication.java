package com.example.dbeventservice;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EntityScan(basePackages = "com.example.dbeventservice.model")
@EnableJpaRepositories(basePackages = "com.example.dbeventservice.repositories")
@EnableTransactionManagement
public class DbEventServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbEventServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner testEntityManager(EntityManager entityManager) {
        return args -> {
            System.out.println("✅ Checking entities...");
            Metamodel metamodel = entityManager.getMetamodel();
            metamodel.getEntities().forEach(e -> System.out.println("🧩 Entity found: " + e.getName()));
        };
    }


}
