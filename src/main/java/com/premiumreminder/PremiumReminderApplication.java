package com.premiumreminder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PremiumReminderApplication {
    public static void main(String[] args) {
        SpringApplication.run(PremiumReminderApplication.class, args);
    }
}
