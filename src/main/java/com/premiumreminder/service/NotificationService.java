package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.Policy;
import com.premiumreminder.model.ReminderLog;
import com.premiumreminder.repository.ReminderLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends premium-due reminders via WhatsApp and email only.
 * SMS support (Fast2SMS) has been removed per the current requirement - see
 * git history / the old SmsService if it ever needs to come back.
 *
 * WhatsApp template in use (approved, 7 positional params) - covers both the
 * "due soon" and "overdue" cases via {{4}} (status phrase) and {{7}} (labeled date),
 * since template bodies can't branch on conditions themselves:
 *   Dear {{1}},
 *   This is a reminder that your {{2}} premium payment with {{3}} is {{4}}.
 *   Policy Number: {{5}}
 *   Amount Due: ₹{{6}}
 *   {{7}}
 *   Please make the payment at the earliest to avoid lapse of coverage.
 * Param order below MUST match this exactly:
 *   name, category, insurerName, statusPhrase, policyNumber, amount, labeledDate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final ReminderLogRepository reminderLogRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public void sendPremiumReminder(Policy policy) {
        String customerName = policy.getCustomer().getFullName();
        String dueDateStr = policy.getNextDueDate().format(DATE_FMT);
        boolean overdue = policy.getNextDueDate().isBefore(LocalDate.now());
        String insurerName = policy.getInsurerName() != null ? policy.getInsurerName() : "your insurer";
        String category = policy.getCategory() != null ? policy.getCategory().getLabel() : "Insurance";
        String statusPhrase = overdue ? "now OVERDUE" : "due soon";
        String labeledDate = (overdue ? "Was Due On: " : "Due On: ") + dueDateStr;

        String subject = overdue
                ? "Premium overdue - " + category + " Policy " + policy.getPolicyNumber()
                : "Premium due soon - " + category + " Policy " + policy.getPolicyNumber();

        String body = String.format(
                "Dear %s,%n%n" +
                        "This is a reminder that your %s premium payment with %s is %s.%n%n" +
                        "Policy Number: %s%n" +
                        "Amount Due: Rs. %s%n" +
                        "%s%n%n" +
                        "Please make the payment at the earliest to keep your policy active and avoid lapse of coverage.%n%n" +
                        "Regards,%nKFS Team",
                customerName,
                category,
                insurerName,
                statusPhrase,
                policy.getPolicyNumber(),
                policy.getPremiumAmount(),
                labeledDate
        );

        String email = policy.getCustomer().getEmail();
        if (email == null || email.isBlank()) {
            logAttempt(policy, ReminderLog.Channel.EMAIL, null, false, "Skipped - no email on file");
        } else {
            try {
                emailService.send(email, subject, body);
                logAttempt(policy, ReminderLog.Channel.EMAIL, null, true, "Sent");
            } catch (Exception e) {
                log.error("Email failed for policy {}: {}", policy.getId(), e.getMessage());
                logAttempt(policy, ReminderLog.Channel.EMAIL, null, false, e.getMessage());
            }
        }

        // WhatsApp - Meta Cloud API expects the full number with country code, no "+"
        String rawNumber = effectiveWhatsAppNumber(policy.getCustomer());
        try {
            String digitsOnly = rawNumber.replaceAll("\\D", "");
            String waNumber = digitsOnly.length() == 10 ? "91" + digitsOnly : digitsOnly;
            List<String> templateParams = List.of(
                    customerName,
                    category,
                    insurerName,
                    statusPhrase,
                    policy.getPolicyNumber(),
                    policy.getPremiumAmount().toString(),
                    labeledDate
            );
            whatsAppService.sendTemplate(waNumber, templateParams, policy.getCustomer().getMessageLanguage());
            logAttempt(policy, ReminderLog.Channel.WHATSAPP, rawNumber, true, "Sent");
        } catch (Exception e) {
            log.error("WhatsApp failed for policy {}: {}", policy.getId(), e.getMessage());
            logAttempt(policy, ReminderLog.Channel.WHATSAPP, rawNumber, false, e.getMessage());
        }
    }

    private void logAttempt(Policy policy, ReminderLog.Channel channel, String phone, boolean success, String detail) {
        ReminderLog logEntry = new ReminderLog();
        logEntry.setPolicy(policy);
        logEntry.setCustomerName(policy.getCustomer().getFullName());
        logEntry.setPolicyNumber(policy.getPolicyNumber());
        logEntry.setChannel(channel);
        logEntry.setPhone(phone);
        logEntry.setSuccess(success);
        logEntry.setDetail(detail);
        reminderLogRepository.save(logEntry);
    }

    private String effectiveWhatsAppNumber(Customer customer) {
        String whatsapp = customer.getWhatsappNumber();
        return (whatsapp != null && !whatsapp.isBlank()) ? whatsapp : customer.getPhone();
    }
}