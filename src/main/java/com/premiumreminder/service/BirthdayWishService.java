package com.premiumreminder.service;

import com.premiumreminder.model.BirthdayLog;
import com.premiumreminder.model.Customer;
import com.premiumreminder.model.CustomerRelative;
import com.premiumreminder.repository.BirthdayLogRepository;
import com.premiumreminder.repository.CustomerRelativeRepository;
import com.premiumreminder.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Comparator;
import java.util.List;

/**
 * Sends birthday wishes via WhatsApp and email only (no SMS). The scheduler that calls
 * runDailyBirthdayWishes() fires once daily at 10:00 AM IST - see BirthdayWishScheduler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BirthdayWishService {

    private final CustomerRepository customerRepository;
    private final CustomerRelativeRepository relativeRepository;
    private final BirthdayLogRepository birthdayLogRepository;

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;

    public record BirthdayEntry(Customer customer, LocalDate nextBirthday) {}
    public record RelativeBirthdayEntry(CustomerRelative relative, LocalDate nextBirthday) {}

    public List<BirthdayEntry> findAllWithDob() {
        return customerRepository.findAll().stream()
                .filter(c -> c.getDateOfBirth() != null)
                .map(c -> new BirthdayEntry(c, nextBirthday(c.getDateOfBirth())))
                .sorted(Comparator.comparing(BirthdayEntry::nextBirthday))
                .toList();
    }

    public List<RelativeBirthdayEntry> findAllRelativesWithDob() {
        return relativeRepository.findAllWithCustomer().stream()
                .map(r -> new RelativeBirthdayEntry(r, nextBirthday(r.getDateOfBirth())))
                .sorted(Comparator.comparing(RelativeBirthdayEntry::nextBirthday))
                .toList();
    }

    public List<Customer> findBirthdaysToday() {
        MonthDay today = MonthDay.from(LocalDate.now());
        return customerRepository.findAll().stream()
                .filter(c -> c.getDateOfBirth() != null)
                .filter(c -> MonthDay.from(c.getDateOfBirth()).equals(today))
                .toList();
    }

    public List<CustomerRelative> findRelativeBirthdaysToday() {
        MonthDay today = MonthDay.from(LocalDate.now());
        return relativeRepository.findAllWithCustomer().stream()
                .filter(r -> MonthDay.from(r.getDateOfBirth()).equals(today))
                .toList();
    }

    private LocalDate nextBirthday(LocalDate dob) {
        LocalDate today = LocalDate.now();
        LocalDate thisYear = dob.withYear(today.getYear());
        return thisYear.isBefore(today) ? thisYear.plusYears(1) : thisYear;
    }

    public void sendWishNow(Long customerId) {
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        sendWish(c);
    }

    public void sendRelativeWishNow(Long relativeId) {
        CustomerRelative r = relativeRepository.findById(relativeId)
                .orElseThrow(() -> new IllegalArgumentException("Relative not found: " + relativeId));
        sendWish(r);
    }

    /**
     * Runs both the policyholder and relative birthday scans for today and sends
     * wishes to whoever matches. Returns the combined count for the dashboard summary.
     */
    public int runDailyBirthdayWishes() {
        List<Customer> todaysCustomers = findBirthdaysToday();
        List<CustomerRelative> todaysRelatives = findRelativeBirthdaysToday();

        todaysCustomers.forEach(this::sendWish);
        todaysRelatives.forEach(this::sendWish);

        return todaysCustomers.size() + todaysRelatives.size();
    }

    private void sendWish(Customer c) {
        String subject = "Happy Birthday, " + c.getFullName() + "! \uD83C\uDF89";
        String body = "Dear " + c.getFullName() + ",\n\nWishing you a very happy birthday! "
                + "Thank you for being a valued customer.\n\nWarm regards,\nKFS Team";

        if (c.getEmail() == null || c.getEmail().isBlank()) {
            log(c.getId(), c.getFullName(), null, null, "EMAIL", null, false, "Skipped - no email on file");
        } else {
            try {
                emailService.send(c.getEmail(), subject, body);
                log(c.getId(), c.getFullName(), null, null, "EMAIL", null, true, "Sent");
            } catch (Exception e) {
                log.error("Birthday email failed for customer {}", c.getId(), e);
                log(c.getId(), c.getFullName(), null, null, "EMAIL", null, false, e.getMessage());
            }
        }

        String rawNumber = effectiveWhatsAppNumber(c);
        try {
            String mobile = rawNumber.replaceFirst("^\\+", "");
            whatsAppService.sendBirthdayTemplate(mobile, List.of(c.getFullName()), c.getMessageLanguage());
            log(c.getId(), c.getFullName(), null, null, "WHATSAPP", rawNumber, true, "Sent");
        } catch (Exception e) {
            log.error("Birthday WhatsApp message failed for customer {}", c.getId(), e);
            log(c.getId(), c.getFullName(), null, null, "WHATSAPP", rawNumber, false, e.getMessage());
        }
    }

    private void sendWish(CustomerRelative r) {
        String subject = "Happy Birthday, " + r.getFullName() + "! \uD83C\uDF89";
        String body = "Dear " + r.getFullName() + ",\n\nWishing you a very happy birthday! "
                + "Warm wishes from all of us at KFS.\n\nWarm regards,\nKFS Team";

        Long customerId = r.getCustomer().getId();
        String relation = r.getRelation();

        try {
            emailService.send(r.getEmail(), subject, body);
            log(customerId, r.getFullName(), r.getId(), relation, "EMAIL", null, true, "Sent");
        } catch (Exception e) {
            log.error("Birthday email failed for relative {}", r.getId(), e);
            log(customerId, r.getFullName(), r.getId(), relation, "EMAIL", null, false, e.getMessage());
        }

        String rawNumber = r.getPhone();
        try {
            String mobile = rawNumber.replaceFirst("^\\+", "");
            whatsAppService.sendBirthdayTemplate(mobile, List.of(r.getFullName()), r.getMessageLanguage());
            log(customerId, r.getFullName(), r.getId(), relation, "WHATSAPP", rawNumber, true, "Sent");
        } catch (Exception e) {
            log.error("Birthday WhatsApp message failed for relative {}", r.getId(), e);
            log(customerId, r.getFullName(), r.getId(), relation, "WHATSAPP", rawNumber, false, e.getMessage());
        }
    }

    private void log(Long customerId, String recipientName, Long relativeId, String relation,
                     String channel, String phone, boolean success, String message) {
        BirthdayLog entry = new BirthdayLog();
        entry.setCustomerId(customerId);
        entry.setCustomerName(recipientName);
        entry.setRelativeId(relativeId);
        entry.setRelation(relation);
        entry.setChannel(channel);
        entry.setPhone(phone);
        entry.setSuccess(success);
        entry.setMessage(message);
        birthdayLogRepository.save(entry);
    }

    private String effectiveWhatsAppNumber(Customer c) {
        String whatsapp = c.getWhatsappNumber();
        return (whatsapp != null && !whatsapp.isBlank()) ? whatsapp : c.getPhone();
    }
}