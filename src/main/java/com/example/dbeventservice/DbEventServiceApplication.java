package com.example.dbeventservice;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EntityScan("com.example.dbeventservice.models")
@EnableJpaRepositories("com.example.dbeventservice.repositories")
@EnableTransactionManagement
public class DbEventServiceApplication {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public static void main(String[] args) {
        SpringApplication.run(DbEventServiceApplication.class, args);
    }

    @PostConstruct
    public void printDatasourceUrl() {
        System.out.println("✅ Resolved DB URL: " + datasourceUrl);
    }
}
