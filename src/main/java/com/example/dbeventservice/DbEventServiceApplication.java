package com.example.dbeventservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DbEventServiceApplication {

    public static void main(String[] args) {
        System.out.println("DATABASE_URL = " + System.getenv("DATABASE_URL"));
        SpringApplication.run(DbEventServiceApplication.class, args);
    }

}
