package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.ReminderLog;
import com.premiumreminder.repository.ReminderLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmailService emailService;
    private final SmsService smsService;
    private final WhatsAppService whatsAppService;
    private final ReminderLogRepository reminderLogRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public void sendPremiumReminder(Customer customer) {
        String dueDateStr = customer.getNextDueDate().format(DATE_FMT);
        boolean overdue = customer.getNextDueDate().isBefore(java.time.LocalDate.now());

        String subject = overdue
                ? "Premium overdue - Policy " + customer.getPolicyNumber()
                : "Premium due soon - Policy " + customer.getPolicyNumber();

        String body = String.format(
                "Dear %s,%n%nThis is a reminder that the premium of %s for your policy %s (%s) is %s %s.%n" +
                        "Please make the payment at the earliest to keep your policy active.%n%nRegards,%nInsurance Team",
                customer.getFullName(),
                customer.getPremiumAmount(),
                customer.getPolicyNumber(),
                customer.getInsurerName() != null ? customer.getInsurerName() : "your insurer",
                overdue ? "overdue since" : "due on",
                dueDateStr
        );

        String digitsOnly = customer.getPhone().replaceAll("\\D", "");

        // Email
        try {
            emailService.send(customer.getEmail(), subject, body);
            logAttempt(customer, ReminderLog.Channel.EMAIL, true, "Sent");
        } catch (Exception e) {
            log.error("Email failed for customer {}: {}", customer.getId(), e.getMessage());
            logAttempt(customer, ReminderLog.Channel.EMAIL, false, e.getMessage());
        }

        // SMS - Fast2SMS Quick route expects a bare 10-digit Indian number (no "+", no country code)
        try {
            String mobile = digitsOnly.length() > 10 ? digitsOnly.substring(digitsOnly.length() - 10) : digitsOnly;
            String smsBody = String.format(
                    "Dear %s, your premium of %s for policy %s is %s %s. Please pay to keep your policy active.",
                    customer.getFullName(),
                    customer.getPremiumAmount(),
                    customer.getPolicyNumber(),
                    overdue ? "overdue since" : "due on",
                    dueDateStr
            );
            smsService.send(mobile, smsBody);
            logAttempt(customer, ReminderLog.Channel.SMS, true, "Sent");
        } catch (Exception e) {
            log.error("SMS failed for customer {}: {}", customer.getId(), e.getMessage());
            logAttempt(customer, ReminderLog.Channel.SMS, false, e.getMessage());
        }

        // WhatsApp - Meta Cloud API expects the full number with country code, no "+", e.g. "919812345678"
        try {
            String waNumber = digitsOnly.length() == 10 ? "91" + digitsOnly : digitsOnly;
            List<String> templateParams = List.of(
                    customer.getFullName(),
                    customer.getPremiumAmount().toString(),
                    customer.getPolicyNumber(),
                    dueDateStr
            );
            whatsAppService.sendTemplate(waNumber, templateParams);
            logAttempt(customer, ReminderLog.Channel.WHATSAPP, true, "Sent");
        } catch (Exception e) {
            log.error("WhatsApp failed for customer {}: {}", customer.getId(), e.getMessage());
            logAttempt(customer, ReminderLog.Channel.WHATSAPP, false, e.getMessage());
        }
    }

    private void logAttempt(Customer customer, ReminderLog.Channel channel, boolean success, String detail) {
        ReminderLog logEntry = new ReminderLog();
        logEntry.setCustomer(customer);
        logEntry.setChannel(channel);
        logEntry.setSuccess(success);
        logEntry.setDetail(detail);
        reminderLogRepository.save(logEntry);
    }
}