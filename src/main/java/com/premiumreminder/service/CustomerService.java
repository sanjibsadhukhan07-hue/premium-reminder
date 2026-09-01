package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.PremiumFrequency;
import com.premiumreminder.repository.CustomerRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    /**
     * Applies edits from the customer form onto the persisted record.
     * The form only carries a subset of fields (see customer-form.html) — it
     * knows nothing about the policy document or payment/reminder tracking
     * state, so a naive save(formCustomer) would null those columns out on
     * every edit. Load the existing row and copy in only what the form owns.
     */
    @Transactional
    public Customer save(Customer formCustomer) {
        if (formCustomer.getId() == null) {
            return customerRepository.save(formCustomer);
        }

        Customer existing = findById(formCustomer.getId());

        existing.setFullName(formCustomer.getFullName());
        existing.setEmail(formCustomer.getEmail());
        existing.setPhone(formCustomer.getPhone());
        existing.setPolicyNumber(formCustomer.getPolicyNumber());
        existing.setInsurerName(formCustomer.getInsurerName());
        existing.setDateOfBirth(formCustomer.getDateOfBirth());
        existing.setPremiumAmount(formCustomer.getPremiumAmount());
        existing.setNextDueDate(formCustomer.getNextDueDate());
        existing.setPremiumFrequency(formCustomer.getPremiumFrequency());
        existing.setReminderWindowDays(formCustomer.getReminderWindowDays());
        existing.setActive(formCustomer.isActive());

        // Deliberately untouched: policyDocFileName/ContentType/Data, paid,
        // lastPaidDate, lastReminderSentDate, createdAt — none of these are on
        // this form, so they should only change via their own dedicated flows
        // (savePolicyDoc, markPaid, the reminder scheduler).

        return customerRepository.save(existing);
    }

    public void delete(Long id) {
        customerRepository.deleteById(id);
    }

    /**
     * Attaches/replaces the policy document file for a customer.
     * Stored directly in Postgres rather than local disk, since local disk
     * is wiped on every Railway redeploy.
     */
    @Transactional
    public void savePolicyDoc(Long id, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No policy document file was provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)) {
            throw new IllegalArgumentException("Only PDF files are allowed for policy documents");
        }

        Customer customer = findById(id);
        customer.setPolicyDocFileName(file.getOriginalFilename());
        customer.setPolicyDocContentType(contentType);
        customer.setPolicyDocData(file.getBytes());
        customerRepository.save(customer);
    }

    /**
     * Marks the current premium cycle as paid and rolls the due date forward
     * according to the customer's premium frequency (yearly / half-yearly /
     * quarterly / 3-yearly), using calendar-correct date math rather than a
     * fixed day count.
     */
    @Transactional
    public Customer markPaid(Long id) {
        Customer customer = findById(id);
        customer.setLastPaidDate(LocalDate.now());
        customer.setNextDueDate(customer.getPremiumFrequency().nextDueDate(customer.getNextDueDate()));
        customer.setLastReminderSentDate(null);
        customer.setPaid(false);
        return customerRepository.save(customer);
    }

    public List<Customer> findDueForReminderToday() {
        LocalDate today = LocalDate.now();
        return customerRepository.findByActiveTrueAndPaidFalse().stream()
                .filter(c -> !today.isBefore(c.getNextDueDate().minusDays(c.getReminderWindowDays())))
                .filter(c -> c.getLastReminderSentDate() == null || !c.getLastReminderSentDate().isEqual(today))
                .toList();
    }

    /**
     * Bulk imports/updates customers from a CSV file.
     * Matching is done on policyNumber: if a customer with that policy number
     * already exists, it is updated in place; otherwise a new customer is created.
     * Required columns: fullName, email, phone, policyNumber, premiumAmount, nextDueDate, premiumFrequency
     * Optional columns: insurerName, dateOfBirth, reminderWindowDays, active
     * premiumFrequency accepts: YEARLY, HALF_YEARLY, QUARTERLY, THREE_YEARLY (case-insensitive)
     */
    @Transactional
    public CsvImportResult importFromCsv(MultipartFile file) throws IOException {
        CsvImportResult result = new CsvImportResult();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setIgnoreEmptyLines(true)
                    .build();

            try (CSVParser parser = format.parse(reader)) {
                int rowNum = 1; // header row
                for (CSVRecord record : parser) {
                    rowNum++;
                    result.totalRows++;
                    try {
                        processRow(record, result);
                    } catch (Exception e) {
                        result.errors.add("Row " + rowNum + ": " + e.getMessage());
                    }
                }
            }
        }

        return result;
    }

    public List<Customer> search(String q) {
        if (q == null || q.isBlank()) {
            return findAll();
        }
        return customerRepository.search(q.trim());
    }

    private void processRow(CSVRecord record, CsvImportResult result) {
        String policyNumber = requireField(record, "policyNumber");
        String fullName = requireField(record, "fullName");
        String email = requireField(record, "email");
        String phone = requireField(record, "phone");
        BigDecimal premiumAmount = new BigDecimal(requireField(record, "premiumAmount"));
        LocalDate nextDueDate = LocalDate.parse(requireField(record, "nextDueDate"));
        PremiumFrequency premiumFrequency = requireEnumField(record, "premiumFrequency");

        Customer customer = customerRepository.findByPolicyNumber(policyNumber).orElse(null);
        boolean isNew = customer == null;
        if (isNew) {
            customer = new Customer();
        }

        customer.setFullName(fullName);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setPolicyNumber(policyNumber);
        customer.setInsurerName(optionalField(record, "insurerName", customer.getInsurerName()));
        customer.setDateOfBirth(optionalDateField(record, "dateOfBirth", customer.getDateOfBirth()));
        customer.setPremiumAmount(premiumAmount);
        customer.setNextDueDate(nextDueDate);
        customer.setPremiumFrequency(premiumFrequency);
        customer.setReminderWindowDays(optionalIntField(record, "reminderWindowDays", 30));
        customer.setActive(optionalBooleanField(record, "active", true));

        customerRepository.save(customer);

        if (isNew) {
            result.imported++;
        } else {
            result.updated++;
        }
    }

    private String requireField(CSVRecord record, String name) {
        if (!record.isMapped(name) || record.get(name) == null || record.get(name).isBlank()) {
            throw new IllegalArgumentException("Missing required field '" + name + "'");
        }
        return record.get(name).trim();
    }

    private PremiumFrequency requireEnumField(CSVRecord record, String name) {
        String raw = requireField(record, name);
        try {
            return PremiumFrequency.valueOf(raw.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid '" + name + "': " + raw + " (expected YEARLY, HALF_YEARLY, QUARTERLY, or THREE_YEARLY)");
        }
    }

    private String optionalField(CSVRecord record, String name, String fallback) {
        if (!record.isMapped(name) || record.get(name) == null || record.get(name).isBlank()) {
            return fallback;
        }
        return record.get(name).trim();
    }

    private LocalDate optionalDateField(CSVRecord record, String name, LocalDate fallback) {
        if (!record.isMapped(name) || record.get(name) == null || record.get(name).isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(record.get(name).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date for '" + name + "': " + record.get(name)
                    + " (expected YYYY-MM-DD)");
        }
    }

    private int optionalIntField(CSVRecord record, String name, int fallback) {
        if (!record.isMapped(name) || record.get(name) == null || record.get(name).isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(record.get(name).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for '" + name + "': " + record.get(name));
        }
    }

    private boolean optionalBooleanField(CSVRecord record, String name, boolean fallback) {
        if (!record.isMapped(name) || record.get(name) == null || record.get(name).isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(record.get(name).trim());
    }

    @Getter
    public static class CsvImportResult {
        private int totalRows = 0;
        private int imported = 0;
        private int updated = 0;
        private final List<String> errors = new ArrayList<>();

        public int getSkipped() {
            return errors.size();
        }
    }
}