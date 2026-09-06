package com.premiumreminder.scheduler;

import com.premiumreminder.service.BirthdayWishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends birthday wishes once a day at 10:00 AM India Standard Time. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BirthdayWishScheduler {

    private final BirthdayWishService birthdayWishService;

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
    public void runDailyBirthdayWishes() {
        int count = birthdayWishService.runDailyBirthdayWishes();
        log.info("Sent birthday wishes to {} customer(s) today", count);
    }
}
