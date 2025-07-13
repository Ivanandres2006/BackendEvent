package com.example.dbeventservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.example.dbeventservice.repositories")
@EnableTransactionManagement
public class DbEventServiceApplication {

    public static void main(String[] args) {
        System.out.println("JDBC drivers: " + java.sql.DriverManager.getDrivers());
        System.out.println("DATABASE_URL = " + System.getenv("JDBC_DATABASE_URL"));
        SpringApplication.run(DbEventServiceApplication.class, args);
    }

}
