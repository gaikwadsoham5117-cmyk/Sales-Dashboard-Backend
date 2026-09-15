package com.example.SalesDashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SalesDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesDashboardApplication.class, args);
    }
}