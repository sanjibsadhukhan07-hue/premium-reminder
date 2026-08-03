package com.premiumreminder.scheduler;

import com.premiumreminder.model.Customer;
import com.premiumreminder.service.CustomerService;
import com.premiumreminder.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PremiumReminderScheduler {

    private final CustomerService customerService;
    private final NotificationService notificationService;

    // Runs every day at 09:00 server time. Change the cron in application.yml (app.scheduler.cron) if needed.
    @Scheduled(cron = "${app.scheduler.cron:0 0 9 * * *}")
    @Transactional
    public void runDailyReminders() {
        List<Customer> due = customerService.findDueForReminderToday();
        log.info("Daily reminder run: {} customer(s) due", due.size());

        for (Customer customer : due) {
            notificationService.sendPremiumReminder(customer);
            customer.setLastReminderSentDate(LocalDate.now());
            customerService.save(customer);
        }
    }
}
