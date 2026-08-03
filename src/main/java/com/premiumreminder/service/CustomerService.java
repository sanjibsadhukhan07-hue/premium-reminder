package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.repository.CustomerRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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

    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }

    public void delete(Long id) {
        customerRepository.deleteById(id);
    }

    /**
     * Marks the current premium cycle as paid and rolls the due date forward
     * by the customer's renewal cycle (e.g. +365 days for annual policies).
     */
    @Transactional
    public Customer markPaid(Long id) {
        Customer customer = findById(id);
        customer.setLastPaidDate(LocalDate.now());
        customer.setNextDueDate(customer.getNextDueDate().plusDays(customer.getRenewalCycleDays()));
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
     * Required columns: fullName, email, phone, policyNumber, premiumAmount, nextDueDate
     * Optional columns: insurerName, reminderWindowDays, renewalCycleDays, active
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

    private void processRow(CSVRecord record, CsvImportResult result) {
        String policyNumber = requireField(record, "policyNumber");
        String fullName = requireField(record, "fullName");
        String email = requireField(record, "email");
        String phone = requireField(record, "phone");
        BigDecimal premiumAmount = new BigDecimal(requireField(record, "premiumAmount"));
        LocalDate nextDueDate = LocalDate.parse(requireField(record, "nextDueDate"));

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
        customer.setPremiumAmount(premiumAmount);
        customer.setNextDueDate(nextDueDate);
        customer.setReminderWindowDays(optionalIntField(record, "reminderWindowDays", 30));
        customer.setRenewalCycleDays(optionalIntField(record, "renewalCycleDays", 365));
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

    private String optionalField(CSVRecord record, String name, String fallback) {
        if (!record.isMapped(name) || record.get(name) == null || record.get(name).isBlank()) {
            return fallback;
        }
        return record.get(name).trim();
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