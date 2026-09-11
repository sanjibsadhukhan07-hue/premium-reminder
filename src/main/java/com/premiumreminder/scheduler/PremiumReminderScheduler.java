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

@Component
@RequiredArgsConstructor
@Slf4j
public class PremiumReminderScheduler {

    private final PolicyService policyService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void runDailyRollover() {
        log.info("runDailyRollover Started");
        int rolled = policyService.rolloverPaidPolicies();
        if (rolled > 0) {
            log.info("Rolled {} paid polic(y/ies) into their next premium cycle", rolled);
        }
    }

    // New: admin "due tomorrow" alert. Runs once daily, independent of the alternate-day
    // reminder cadence below - the admin should hear about every policy due tomorrow,
    // not just on reminder days.
    @Scheduled(cron = "${app.scheduler.admin-alert-cron:0 30 8 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void runDueTomorrowAdminAlert() {
        List<Policy> dueTomorrow = policyService.findDueTomorrow();
        if (dueTomorrow.isEmpty()) {
            log.info("No policies due tomorrow - skipping admin alert");
            return;
        }
        for (Policy policy : dueTomorrow) {
            notificationService.sendAdminDueTomorrowAlert(policy);
        }
        log.info("Sent {} admin due-tomorrow alert(s)", dueTomorrow.size());
    }

//    // Every 5 minutes: unpaid policies whose nextDueDate is more than 45 days out get
//    // flagged paid=true immediately, without moving nextDueDate - independent of the
//    // 6am rollover job, which only advances/resets policies that are ALREADY paid.
//    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Kolkata")
//    @Transactional
//    public void runFarFutureAutoPaidMark() {
//        log.info("runFarFutureAutoPaidMark Started");
//        int marked = policyService.markFarFuturePoliciesAsPaid();
//        if (marked > 0) {
//            log.info("Marked {} polic(y/ies) as paid (nextDueDate more than 45 days out)", marked);
//        }
//    }

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