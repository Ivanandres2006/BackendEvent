package com.example.dbeventservice;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
public class DbEventServiceApplication {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public static void main(String[] args) {
        System.out.println("JDBC drivers: " + java.sql.DriverManager.getDrivers());
        System.out.println("DATABASE_URL = " + System.getenv("JDBC_DATABASE_URL"));
        SpringApplication.run(DbEventServiceApplication.class, args);
    }

    @PostConstruct
    public void printDatasourceUrl() {
        System.out.println("🔍 JDBC URL in use: " + datasourceUrl);
    }

}
