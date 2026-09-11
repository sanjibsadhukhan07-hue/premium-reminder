package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.Policy;
import com.premiumreminder.model.PolicyCategory;
import com.premiumreminder.model.PremiumFrequency;
import com.premiumreminder.repository.CustomerRepository;
import com.premiumreminder.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports a single Excel row in its own, independent transaction (REQUIRES_NEW).
 * This is deliberately split out of PolicyService: a bulk import processes many
 * rows in a loop, and if any one row violates a DB constraint (e.g. duplicate
 * email), Hibernate's session for that transaction is poisoned - every entity
 * saved afterwards in the SAME transaction then fails too ("null id" assertion
 * errors), and the whole import silently rolls back. Giving each row its own
 * transaction means a bad row rolls back alone; every other row still commits.
 */
@Service
@RequiredArgsConstructor
public class ExcelRowImportService {

    private final CustomerRepository customerRepository;
    private final PolicyRepository policyRepository;

    public record RowOutcome(boolean newCustomer, boolean newPolicy, boolean updatedPolicy) {
        static final RowOutcome CUSTOMER_ONLY_NEW = new RowOutcome(true, false, false);
        static final RowOutcome CUSTOMER_ONLY_EXISTING = new RowOutcome(false, false, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowOutcome importRow(Row row, Map<String, Integer> col, DataFormatter fmt,
                                PolicyCategory sheetDefaultCategory) {
        String fullName = requireField(row, col, fmt, "fullname");
        String phone = requireField(row, col, fmt, "phone");
        String email = optionalField(row, col, fmt, "email", null);

        Customer customer = customerRepository.findByPhone(phone).orElse(null);
        boolean newCustomer = customer == null;
        if (newCustomer) {
            customer = new Customer();
            customer.setPhone(phone);
        }
        customer.setFullName(fullName);
        if (email != null) customer.setEmail(email);
        String dob = optionalField(row, col, fmt, "dateofbirth", null);
        if (dob != null) customer.setDateOfBirth(parseDate(dob));
        customer = customerRepository.saveAndFlush(customer);

        String policyNumber = optionalField(row, col, fmt, "policynumber", null);
        if (policyNumber == null || policyNumber.isBlank()) {
            return newCustomer ? RowOutcome.CUSTOMER_ONLY_NEW : RowOutcome.CUSTOMER_ONLY_EXISTING;
        }

        BigDecimal premiumAmount = new BigDecimal(requireField(row, col, fmt, "premiumamount").replaceAll("[^0-9.]", ""));
        LocalDate nextDueDate = parseDate(requireField(row, col, fmt, "nextduedate"));

        Policy policy = policyRepository.findByPolicyNumber(policyNumber).orElse(null);
        boolean newPolicy = policy == null;
        if (newPolicy) {
            policy = new Policy();
            policy.setPolicyNumber(policyNumber);
        }
        policy.setCustomer(customer);
        String categoryRaw = optionalField(row, col, fmt, "category", null);
        policy.setCategory(categoryRaw != null ? parseCategory(categoryRaw) : sheetDefaultCategory);
        policy.setInsurerName(optionalField(row, col, fmt, "insurername", policy.getInsurerName()));
        policy.setPolicyHolderName(optionalField(row, col, fmt, "policyholdername", policy.getPolicyHolderName()));
        // officeTag lives on Policy (not Customer) - which office/agent brought THIS
        // policy in, since one customer's policies can come through different offices.
        policy.setOfficeTag(optionalField(row, col, fmt, "officetag", policy.getOfficeTag()));
        policy.setPlanType(optionalField(row, col, fmt, "plantype", policy.getPlanType()));
        policy.setPolicyType(optionalField(row, col, fmt, "policytype", policy.getPolicyType()));
        String sumAssured = optionalField(row, col, fmt, "sumassured", null);
        if (sumAssured != null && !sumAssured.isBlank()) {
            policy.setSumAssured(new BigDecimal(sumAssured.replaceAll("[^0-9.]", "")));
        }
        policy.setHealthCheckupNote(optionalField(row, col, fmt, "healthcheckupnote", policy.getHealthCheckupNote()));
        policy.setVehicleRegistrationNo(optionalField(row, col, fmt, "vehicleregistrationno", policy.getVehicleRegistrationNo()));
        String startDate = optionalField(row, col, fmt, "startdate", null);
        if (startDate != null && !startDate.isBlank()) {
            policy.setStartDate(parseDate(startDate));
        }
        policy.setPremiumAmount(premiumAmount);
        policy.setNextDueDate(nextDueDate);
        policy.setPremiumFrequency(parseFrequency(optionalField(row, col, fmt, "premiumfrequency", "YEARLY")));
        policy.setReminderWindowDays(parseIntOr(optionalField(row, col, fmt, "reminderwindowdays", null), 30));
        policy.setActive(parseBoolOr(optionalField(row, col, fmt, "active", null), true));
        policy.setPaid(parseBoolOr(optionalField(row, col, fmt, "paid", null), policy.isPaid()));

        policyRepository.saveAndFlush(policy);

        return new RowOutcome(newCustomer, newPolicy, !newPolicy);
    }

    private String requireField(Row row, Map<String, Integer> col, DataFormatter fmt, String name) {
        Integer idx = col.get(name);
        String value = idx == null ? null : fmt.formatCellValue(row.getCell(idx)).trim();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field '" + name + "'");
        }
        return value;
    }

    private String optionalField(Row row, Map<String, Integer> col, DataFormatter fmt, String name, String fallback) {
        Integer idx = col.get(name);
        if (idx == null) return fallback;
        String value = fmt.formatCellValue(row.getCell(idx)).trim();
        return value.isBlank() ? fallback : value;
    }

    private LocalDate parseDate(String raw) {
        raw = raw.trim();
        int spaceIdx = raw.indexOf(' ');
        if (spaceIdx > 0 && raw.substring(spaceIdx + 1).contains(":")) {
            raw = raw.substring(0, spaceIdx);
        }
        List<java.time.format.DateTimeFormatter> patterns = List.of(
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
                java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("d-M-yy"),
                java.time.format.DateTimeFormatter.ofPattern("d/M/yy"),
                java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("M/d/yy")
        );
        for (var pattern : patterns) {
            try {
                return LocalDate.parse(raw, pattern);
            } catch (Exception ignored) {
                // try next pattern
            }
        }
        throw new IllegalArgumentException("Unrecognized date format: " + raw + " (use YYYY-MM-DD or DD-MM-YYYY)");
    }

    private PolicyCategory parseCategory(String raw) {
        try {
            return PolicyCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid 'category': " + raw
                    + " (expected HEALTH, MOTOR, LIFE, PERSONAL_ACCIDENT, or OTHER)");
        }
    }

    private PremiumFrequency parseFrequency(String raw) {
        try {
            return PremiumFrequency.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid 'premiumFrequency': " + raw
                    + " (expected YEARLY, HALF_YEARLY, QUARTERLY, or THREE_YEARLY)");
        }
    }

    private int parseIntOr(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer: " + raw);
        }
    }

    private boolean parseBoolOr(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String v = raw.trim();
        if (v.equalsIgnoreCase("yes")) return true;
        if (v.equalsIgnoreCase("no")) return false;
        return Boolean.parseBoolean(v);
    }
}