package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.Policy;
import com.premiumreminder.model.PolicyCategory;
import com.premiumreminder.model.PremiumFrequency;
import com.premiumreminder.repository.CustomerRepository;
import com.premiumreminder.repository.PolicyRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final CustomerRepository customerRepository;

    public Policy findById(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));
    }

    public List<Policy> findAll() {
        return policyRepository.findAll();
    }

    /** Dashboard listing sorted so the soonest (or most overdue) renewal date shows first. */
    public List<Policy> findAllSortedByNextDueDate() {
        return policyRepository.findAllOrderByNextDueDateAsc();
    }

    public List<Policy> findByCustomerId(Long customerId) {
        return policyRepository.findByCustomerId(customerId);
    }

    @Transactional
    public Policy save(Long customerId, Policy formPolicy) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        Policy target;
        if (formPolicy.getId() == null) {
            target = new Policy();
            target.setCustomer(customer);
        } else {
            target = findById(formPolicy.getId());
            target.setCustomer(customer);
        }

        target.setCategory(formPolicy.getCategory());
        target.setPolicyNumber(formPolicy.getPolicyNumber());
        target.setInsurerName(formPolicy.getInsurerName());
        target.setPlanType(formPolicy.getPlanType());
        target.setPolicyType(formPolicy.getPolicyType());
        target.setSumAssured(formPolicy.getSumAssured());
        target.setHealthCheckupNote(formPolicy.getHealthCheckupNote());
        target.setVehicleRegistrationNo(formPolicy.getVehicleRegistrationNo());
        target.setPremiumAmount(formPolicy.getPremiumAmount());
        target.setStartDate(formPolicy.getStartDate());
        target.setNextDueDate(formPolicy.getNextDueDate());
        target.setPremiumFrequency(formPolicy.getPremiumFrequency());
        target.setReminderWindowDays(formPolicy.getReminderWindowDays());
        target.setActive(formPolicy.isActive());

        // Paid status can be corrected directly from the form (e.g. fixing an import,
        // or backfilling a policy that was already settled). This just flips the flag -
        // it does NOT roll nextDueDate forward the way the "Already Paid" button does,
        // since this is a status correction, not "I just paid this cycle's premium."
        if (formPolicy.isPaid() && !target.isPaid()) {
            target.setLastPaidDate(LocalDate.now());
        } else if (!formPolicy.isPaid()) {
            target.setLastPaidDate(null);
        }
        target.setPaid(formPolicy.isPaid());
        // policyDoc*/lastReminderSentDate/previousDueDate are deliberately left untouched
        // here - they only change via savePolicyDoc/markAlreadyPaid/unmarkPaid/the scheduler.

        return policyRepository.save(target);
    }

    public void delete(Long id) {
        policyRepository.deleteById(id);
    }

    public void deleteAll(List<Long> ids) {
        policyRepository.deleteAllById(ids);
    }

    @Transactional
    public void savePolicyDoc(Long id, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No policy document file was provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)) {
            throw new IllegalArgumentException("Only PDF files are allowed for policy documents");
        }
        Policy policy = findById(id);
        policy.setPolicyDocFileName(file.getOriginalFilename());
        policy.setPolicyDocContentType(contentType);
        policy.setPolicyDocData(file.getBytes());
        policyRepository.save(policy);
    }

    /**
     * Flags the current premium as paid AND immediately rolls the due date forward to the
     * next cycle (same day-of-month, advanced by the policy's premium frequency - e.g. +1
     * year for YEARLY, +3 years for THREE_YEARLY, +6 months for HALF_YEARLY, +3 months for
     * QUARTERLY). The pre-rollover date is saved to previousDueDate so unmarkPaid can put
     * it back if this was clicked by mistake.
     */
    @Transactional
    public Policy markAlreadyPaid(Long id) {
        Policy policy = findById(id);
        policy.setPreviousDueDate(policy.getNextDueDate());
        policy.setNextDueDate(policy.getPremiumFrequency().nextDueDate(policy.getNextDueDate()));
        policy.setPaid(true);
        policy.setLastPaidDate(LocalDate.now());
        policy.setLastReminderSentDate(null);
        return policyRepository.save(policy);
    }

    /**
     * Reverts an accidental "mark paid" click - restores the original due date (if one was
     * saved) and resumes reminders for that original cycle.
     */
    @Transactional
    public Policy unmarkPaid(Long id) {
        Policy policy = findById(id);
        if (policy.getPreviousDueDate() != null) {
            policy.setNextDueDate(policy.getPreviousDueDate());
            policy.setPreviousDueDate(null);
        }
        policy.setPaid(false);
        return policyRepository.save(policy);
    }

    /**
     * Daily housekeeping, done in two independent steps since they now fire on
     * different conditions:
     *
     *  1) Advance the due date: any paid policy whose nextDueDate has arrived (today or
     *     earlier) gets rolled forward to its next cycle (calendar-correct via
     *     PremiumFrequency). This naturally only fires once per cycle - once nextDueDate
     *     is in the future, this condition no longer matches on subsequent days.
     *
     *  2) Reset the paid flag: a policy only goes back to paid = false once its (already
     *     advanced) nextDueDate is MORE than 45 days away from today. Until then it stays
     *     marked paid, even though the date has already moved to the next cycle.
     */
    @Transactional
    public int rolloverPaidPolicies() {
        LocalDate today = LocalDate.now();

        List<Policy> toAdvance = policyRepository.findAll().stream()
                .filter(Policy::isPaid)
                .filter(p -> !p.getNextDueDate().isAfter(today))
                .toList();

        for (Policy p : toAdvance) {
            p.setNextDueDate(p.getPremiumFrequency().nextDueDate(p.getNextDueDate()));
            p.setLastReminderSentDate(null);
        }
        policyRepository.saveAll(toAdvance);

        List<Policy> toUnmark = policyRepository.findAll().stream()
                .filter(Policy::isPaid)
                .filter(p -> p.getNextDueDate().isAfter(today.plusDays(45)))
                .toList();

        for (Policy p : toUnmark) {
            p.setPaid(false);
        }
        policyRepository.saveAll(toUnmark);

        return toAdvance.size() + toUnmark.size();
    }

    /** Records that a reminder was just sent for this policy, without touching any other field. */
    @Transactional
    public void markReminderSent(Policy policy) {
        policy.setLastReminderSentDate(LocalDate.now());
        policyRepository.save(policy);
    }

    /**
     * Policies due for a premium reminder today: active, not yet paid, and within
     * reminderWindowDays BEFORE nextDueDate through reminderWindowDays AFTER it
     * (30 days each side by default) - i.e. we keep reminding both leading up to and
     * past a missed renewal, until it's marked paid. TRAVEL policies are excluded
     * entirely - they're short-lived, single-trip covers that don't get "renewed"
     * the way health/motor/life policies do, so no reminder is ever sent for them.
     */
    public List<Policy> findDueForReminderToday() {
        LocalDate today = LocalDate.now();
        return policyRepository.findByActiveTrueAndPaidFalse().stream()
                .filter(p -> p.getCategory() != PolicyCategory.TRAVEL)
                .filter(p -> {
                    LocalDate windowStart = p.getNextDueDate().minusDays(p.getReminderWindowDays());
                    LocalDate windowEnd = p.getNextDueDate().plusDays(p.getReminderWindowDays());
                    return !today.isBefore(windowStart) && !today.isAfter(windowEnd);
                })
                .toList();
    }

    /**
     * Active, unpaid, non-TRAVEL policies whose nextDueDate is exactly tomorrow -
     * used for the admin's "premium due tomorrow" alert.
     */
    public List<Policy> findDueTomorrow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        return policyRepository.findByActiveTrueAndPaidFalse().stream()
                .filter(p -> p.getCategory() != PolicyCategory.TRAVEL)
                .filter(p -> tomorrow.equals(p.getNextDueDate()))
                .toList();
    }

    // ---------------------------------------------------------------------------------
    // Bulk import from Excel (.xlsx). Every sheet in the uploaded workbook is scanned;
    // each is expected to have a header row with (case-insensitive, order-independent)
    // column names:
    //
    //   Required:  fullName, phone, policyNumber, premiumAmount, nextDueDate
    //   Optional:  email, category (HEALTH/MOTOR/LIFE/PERSONAL_ACCIDENT/OTHER, default
    //              HEALTH), insurerName, officeTag, dateOfBirth, planType, policyType,
    //              sumAssured, healthCheckupNote, vehicleRegistrationNo, startDate,
    //              premiumFrequency (YEARLY/HALF_YEARLY/QUARTERLY/THREE_YEARLY, default
    //              YEARLY), reminderWindowDays (default 30), active (default true)
    //
    // Matching: customer is matched/created on phone number; policy is matched/created
    // on policyNumber and linked to that customer.
    // ---------------------------------------------------------------------------------
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("name", "fullname"),
            Map.entry("customername", "fullname"),
            Map.entry("policyholder", "fullname"),
            Map.entry("phoneno", "phone"),
            Map.entry("phonenumber", "phone"),
            Map.entry("mobileno", "phone"),
            Map.entry("mobile", "phone"),
            Map.entry("company", "insurername"),
            Map.entry("insurer", "insurername"),
            Map.entry("healthcheckup", "healthcheckupnote"),
            Map.entry("healthcheckupnote2", "healthcheckupnote"),
            Map.entry("saidv", "sumassured"),
            Map.entry("sa", "sumassured"),
            Map.entry("idv", "sumassured"),
            Map.entry("premium", "premiumamount"),
            Map.entry("startingdate", "startdate"),
            Map.entry("renuwaldate", "nextduedate"),
            Map.entry("renewaldate", "nextduedate"),
            Map.entry("duedate", "nextduedate"),
            Map.entry("policyno", "policynumber"),
            Map.entry("policynum", "policynumber"),
            Map.entry("dob", "dateofbirth"),
            Map.entry("policynameregistrationno", "vehicleregistrationno"),
            Map.entry("registrationno", "vehicleregistrationno"),
            Map.entry("regno", "vehicleregistrationno"),
            Map.entry("frequency", "premiumfrequency"),
            Map.entry("policycategory", "category")
    );

    private static String normalizeHeader(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Transactional
    public ExcelImportResult importFromExcel(MultipartFile file) throws IOException {
        ExcelImportResult result = new ExcelImportResult();

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            DataFormatter formatter = new DataFormatter();

            for (Sheet sheet : workbook) {
                Row headerRow = sheet.getRow(sheet.getFirstRowNum());
                if (headerRow == null) continue;

                Map<String, Integer> columnIndex = new HashMap<>();
                for (Cell cell : headerRow) {
                    String header = formatter.formatCellValue(cell).trim();
                    if (header.isEmpty()) continue;
                    String normalized = normalizeHeader(header);
                    String canonical = HEADER_ALIASES.getOrDefault(normalized, normalized);
                    columnIndex.putIfAbsent(canonical, cell.getColumnIndex());
                }
                if (!columnIndex.containsKey("fullname") && !columnIndex.containsKey("policynumber")) {
                    continue; // not a recognizable data sheet (e.g. a notes/scratch tab), skip quietly
                }

                for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row == null || isBlankRow(row, formatter)) continue;

                    result.totalRows++;
                    try {
                        processRow(row, columnIndex, formatter, result, defaultCategoryForSheet(sheet.getSheetName()));
                    } catch (Exception e) {
                        result.errors.add("Sheet '" + sheet.getSheetName() + "', row " + (rowNum + 1) + ": " + e.getMessage());
                    }
                }
            }
        }

        return result;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }

    private PolicyCategory defaultCategoryForSheet(String sheetName) {
        String s = sheetName.toUpperCase(Locale.ROOT);
        if (s.contains("MOTOR")) return PolicyCategory.MOTOR;
        if (s.contains("TRAVEL")) return PolicyCategory.TRAVEL;
        if ((s.contains("LIFE") || s.contains("TERM")) && !s.contains("HEALTH")) return PolicyCategory.LIFE;
        if (s.contains("ACCIDENT") || s.contains("PERSONAL A")) return PolicyCategory.PERSONAL_ACCIDENT;
        return PolicyCategory.HEALTH;
    }

    private void processRow(Row row, Map<String, Integer> col, DataFormatter fmt, ExcelImportResult result,
                            PolicyCategory sheetDefaultCategory) {
        String fullName = requireField(row, col, fmt, "fullname");
        String phone = requireField(row, col, fmt, "phone");
        String policyNumber = requireField(row, col, fmt, "policynumber");
        BigDecimal premiumAmount = new BigDecimal(requireField(row, col, fmt, "premiumamount").replaceAll("[^0-9.]", ""));
        LocalDate nextDueDate = parseDate(requireField(row, col, fmt, "nextduedate"));
        String email = optionalField(row, col, fmt, "email", null);

        Customer customer = customerRepository.findByPhone(phone).orElse(null);
        boolean newCustomer = customer == null;
        if (newCustomer) {
            customer = new Customer();
            customer.setPhone(phone);
        }
        customer.setFullName(fullName);
        if (email != null) customer.setEmail(email);
        customer.setOfficeTag(optionalField(row, col, fmt, "officetag", customer.getOfficeTag()));
        String dob = optionalField(row, col, fmt, "dateofbirth", null);
        if (dob != null) customer.setDateOfBirth(parseDate(dob));
        customer = customerRepository.save(customer);

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

        policyRepository.save(policy);

        if (newPolicy) result.policiesImported++; else result.policiesUpdated++;
        if (newCustomer) result.customersCreated++;
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
        return Boolean.parseBoolean(raw.trim());
    }

    @Getter
    public static class ExcelImportResult {
        private int totalRows = 0;
        private int customersCreated = 0;
        private int policiesImported = 0;
        private int policiesUpdated = 0;
        private final List<String> errors = new ArrayList<>();

        public int getSkipped() {
            return errors.size();
        }
    }
}