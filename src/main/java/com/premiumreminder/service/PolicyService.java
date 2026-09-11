package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.Policy;
import com.premiumreminder.model.PolicyCategory;
import com.premiumreminder.repository.CustomerRepository;
import com.premiumreminder.repository.PolicyRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final CustomerRepository customerRepository;
    private final ExcelRowImportService excelRowImportService;

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
        target.setPolicyHolderName(formPolicy.getPolicyHolderName());
        target.setPolicyNumber(formPolicy.getPolicyNumber());
        target.setInsurerName(formPolicy.getInsurerName());
        // officeTag now lives on Policy (moved off Customer) - copy it from the form
        // the same way every other policy field is copied here.
        target.setOfficeTag(formPolicy.getOfficeTag());
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
    //   Optional (customer-level):  email, dateOfBirth
    //   Optional (policy-level):    category (HEALTH/MOTOR/LIFE/PERSONAL_ACCIDENT/OTHER,
    //              default HEALTH), insurerName, officeTag (which office/agent brought
    //              THIS policy in - lives on the policy, not the customer, since one
    //              person's policies can come through different offices/agents),
    //              planType, policyType, sumAssured, healthCheckupNote,
    //              vehicleRegistrationNo, startDate, premiumFrequency
    //              (YEARLY/HALF_YEARLY/QUARTERLY/THREE_YEARLY, default YEARLY),
    //              reminderWindowDays (default 30), active (default true),
    //              paid (default false, or preserved on existing policies)
    //
    // Matching: customer is matched/created on phone number; policy is matched/created
    // on policyNumber and linked to that customer.
    //
    // Also accepts our own export format (see ExportService), whose "Customers" and
    // "Policies" sheets use headers like "Customer Phone" / "Customer Email" /
    // "Vehicle Reg. No." / "Paid" (Yes/No) - aliased below to the canonical names.
    //
    // Each row is imported in its own transaction (see ExcelRowImportService) so a
    // single bad/duplicate row can't roll back the rest of the workbook.
    // ---------------------------------------------------------------------------------
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("name", "fullname"),
            Map.entry("customername", "fullname"),
            Map.entry("policyholder", "fullname"),
            Map.entry("phoneno", "phone"),
            Map.entry("phonenumber", "phone"),
            Map.entry("mobileno", "phone"),
            Map.entry("mobile", "phone"),
            Map.entry("customerphone", "phone"),
            Map.entry("customeremail", "email"),
            Map.entry("emailid", "email"),
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
            Map.entry("vehicleregno", "vehicleregistrationno"),
            Map.entry("frequency", "premiumfrequency"),
            Map.entry("policycategory", "category")
    );

    private static String normalizeHeader(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

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
                        // REQUIRES_NEW inside importRow: this row commits (or rolls back)
                        // independently of every other row, so one duplicate-key or
                        // bad-data row can't poison the session for the rest of the import.
                        ExcelRowImportService.RowOutcome outcome = excelRowImportService.importRow(
                                row, columnIndex, formatter, defaultCategoryForSheet(sheet.getSheetName()));
                        if (outcome.newCustomer()) result.customersCreated++;
                        if (outcome.newPolicy()) result.policiesImported++;
                        else if (outcome.updatedPolicy()) result.policiesUpdated++;
                        result.successRows++;
                    } catch (DataIntegrityViolationException e) {
                        result.duplicates++;
                        result.errors.add("Sheet '" + sheet.getSheetName() + "', row " + (rowNum + 1)
                                + ": duplicate or conflicting value (e.g. email already in use)");
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
        if (s.contains("TERM")) return PolicyCategory.TERM;
        if (s.contains("LIFE") && !s.contains("HEALTH")) return PolicyCategory.LIFE;
        if (s.contains("ACCIDENT") || s.contains("PERSONAL A")) return PolicyCategory.PERSONAL_ACCIDENT;
        return PolicyCategory.HEALTH;
    }

    @Getter
    public static class ExcelImportResult {
        private int totalRows = 0;
        private int customersCreated = 0;
        private int policiesImported = 0;
        private int policiesUpdated = 0;
        private int successRows = 0;
        private int duplicates = 0;
        private final List<String> errors = new ArrayList<>();

        public int getSkipped() {
            return errors.size();
        }

        /** Errors that were NOT duplicates (bad data, missing required field, etc.) */
        public int getOtherErrors() {
            return errors.size() - duplicates;
        }
    }
}