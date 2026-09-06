package com.premiumreminder.scheduler;

import com.premiumreminder.model.Policy;
import com.premiumreminder.service.NotificationService;
import com.premiumreminder.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs the premium-reminder sweep every ALTERNATE day at 09:00 IST, covering policies
 * that are within 30 days before OR 30 days after their renewal date and not yet paid
 * (see PolicyService.findDueForReminderToday for the exact window).
 *
 * "Every alternate day" via cron alone would need to reset cleanly across month/year
 * boundaries (a plain day-of-month step like "1,3,5,7..." breaks at month end), so the
 * job is actually scheduled to run daily and then skips itself on odd days using the
 * epoch-day parity check below - this gives true every-other-day behaviour regardless
 * of calendar boundaries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PremiumReminderScheduler {

    private final PolicyService policyService;
    private final NotificationService notificationService;

    /**
     * Runs every day (not just alternate days - date bookkeeping needs to be exact),
     * ahead of the reminder sweep below. Rolls forward any policy that was marked
     * "already paid" once its renewal date has actually arrived, and clears the paid
     * flag so the new cycle starts fresh and unpaid.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void runDailyRollover() {
        int rolled = policyService.rolloverPaidPolicies();
        if (rolled > 0) {
            log.info("Rolled {} paid polic(y/ies) into their next premium cycle", rolled);
        }
    }

    @Scheduled(cron = "${app.scheduler.cron:0 0 9 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void runDailyReminders() {
        if (LocalDate.now().toEpochDay() % 2 != 0) {
            log.info("Skipping premium reminder run - runs every alternate day, today is an off-day");
            return;
        }

        List<Policy> due = policyService.findDueForReminderToday();
        log.info("Premium reminder run: {} polic(y/ies) due", due.size());

        for (Policy policy : due) {
            notificationService.sendPremiumReminder(policy);
            policyService.markReminderSent(policy);
        }
    }


    @Transactional
    public void runDailyRemindersNow() {

        List<Policy> due = policyService.findDueForReminderToday();
        log.info("Premium reminder run: {} polic(y/ies) due", due.size());

        for (Policy policy : due) {
            notificationService.sendPremiumReminder(policy);
            policyService.markReminderSent(policy);
        }
    }
}
