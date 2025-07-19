package com.example.dbeventservice;

import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Metamodel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EntityScan(basePackages = "com.example.dbeventservice")
@EnableJpaRepositories(basePackages = "com.example.dbeventservice.repositories")
@EnableTransactionManagement
public class DbEventServiceApplication {
    @Autowired
    EntityManager entityManager;

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

    @Bean
    public CommandLineRunner forceStartup(EntityManagerFactory emf) {
        return args -> {
            emf.createEntityManager().close();
            System.out.println("✅ EntityManagerFactory initialized manually.");
        };
    }

    @Bean
    public CommandLineRunner listEntities(EntityManager entityManager) {
        return args -> {
            System.out.println("📦 All JPA Entities:");
            entityManager.getMetamodel().getEntities().forEach(entityType -> {
                System.out.println("🧩 " + entityType.getName());
            });
        };
    }



}
