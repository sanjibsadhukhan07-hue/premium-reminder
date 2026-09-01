package com.premiumreminder.scheduler;

import com.premiumreminder.service.BirthdayWishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BirthdayWishScheduler {

    private final BirthdayWishService birthdayWishService;

    // Adjust cron to match whatever time PremiumReminderScheduler already runs at
    @Scheduled(cron = "0 0 8 * * *")
    public void runDailyBirthdayWishes() {
        int count = birthdayWishService.runDailyBirthdayWishes();
        log.info("Sent birthday wishes to {} customer(s) today", count);
    }
}