package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.CustomerRelative;
import com.premiumreminder.model.Policy;
import com.premiumreminder.repository.CustomerRelativeRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final CustomerService customerService;
    private final PolicyService policyService;
    private final BirthdayWishService birthdayWishService;
    private final CustomerRelativeRepository relativeRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    // ---------- Customers + Policies ----------

    public byte[] exportCustomersAndPolicies() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = headerStyle(wb);

            writeCustomerSheet(wb, headerStyle);
            writePolicySheet(wb, headerStyle);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeCustomerSheet(XSSFWorkbook wb, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Customers");
        // Office Tag removed from here - it now lives per-policy (see writePolicySheet),
        // since one customer's policies can come through different offices/agents.
        String[] headers = {
                "ID", "Full Name", "Date of Birth", "Email",
                "Phone", "WhatsApp Number", "Message Language", "Active",
                "Policy Count", "Relative Count", "Earliest Due Date"
        };
        writeHeaderRow(sheet, headers, headerStyle);

        List<Customer> customers = customerService.findAll();
        int rowNum = 1;
        for (Customer c : customers) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(c.getId());
            row.createCell(col++).setCellValue(nullSafe(c.getFullName()));
            row.createCell(col++).setCellValue(c.getDateOfBirth() != null ? c.getDateOfBirth().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(nullSafe(c.getEmail()));
            row.createCell(col++).setCellValue(nullSafe(c.getPhone()));
            row.createCell(col++).setCellValue(nullSafe(c.getWhatsappNumber()));
            row.createCell(col++).setCellValue(nullSafe(c.getMessageLanguage()));
            row.createCell(col++).setCellValue(c.isActive() ? "Yes" : "No");
            row.createCell(col++).setCellValue(c.getPolicies() != null ? c.getPolicies().size() : 0);
            row.createCell(col++).setCellValue(c.getRelatives() != null ? c.getRelatives().size() : 0);
            row.createCell(col).setCellValue(
                    c.getEarliestDueDate() != null ? c.getEarliestDueDate().format(DATE_FMT) : "");
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void writePolicySheet(XSSFWorkbook wb, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Policies");
        // "Customer Name" is the PROPOSER (policy.customer.fullName) - the person this
        // policy is filed under and who reminders go to. "Policy Holder Name" is the
        // separate, optional field for who the policy actually covers when that's a
        // different person (e.g. a proposer taking out a policy for a parent/spouse) -
        // see Policy.policyHolderName / the "Leave blank if same as the prospector"
        // hint on the policy form. Left blank here whenever they're the same person,
        // exactly as it's stored.
        String[] headers = {
                "Policy ID", "Customer Name", "Policy Holder Name", "Customer Phone", "Office Tag", "Insurer",
                "Category", "Health Check-up Note", "Vehicle Reg. No.", "Plan Type", "Policy Type",
                "Premium Frequency", "S.A./IDV", "Premium Amount", "Start Date",
                "Next Due Date", "Previous Due Date", "Policy Number", "Paid",
                "Last Paid Date", "Active", "Customer Email"
        };
        writeHeaderRow(sheet, headers, headerStyle);

        List<Policy> policies = policyService.findAllSortedByNextDueDate();
        int rowNum = 1;
        for (Policy p : policies) {
            Customer c = p.getCustomer();
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(p.getId());
            row.createCell(col++).setCellValue(c != null ? nullSafe(c.getFullName()) : "");
            row.createCell(col++).setCellValue(nullSafe(p.getPolicyHolderName()));
            row.createCell(col++).setCellValue(c != null ? nullSafe(c.getPhone()) : "");
            row.createCell(col++).setCellValue(nullSafe(p.getOfficeTag()));
            row.createCell(col++).setCellValue(nullSafe(p.getInsurerName()));
            row.createCell(col++).setCellValue(p.getCategory() != null ? p.getCategory().name() : "");
            row.createCell(col++).setCellValue(nullSafe(p.getHealthCheckupNote()));
            row.createCell(col++).setCellValue(nullSafe(p.getVehicleRegistrationNo()));
            row.createCell(col++).setCellValue(nullSafe(p.getPlanType()));
            row.createCell(col++).setCellValue(nullSafe(p.getPolicyType()));
            row.createCell(col++).setCellValue(p.getPremiumFrequency() != null ? p.getPremiumFrequency().name() : "");
            row.createCell(col++).setCellValue(p.getSumAssured() != null ? p.getSumAssured().doubleValue() : 0d);
            row.createCell(col++).setCellValue(p.getPremiumAmount() != null ? p.getPremiumAmount().doubleValue() : 0d);
            row.createCell(col++).setCellValue(p.getStartDate() != null ? p.getStartDate().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(p.getNextDueDate() != null ? p.getNextDueDate().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(p.getPreviousDueDate() != null ? p.getPreviousDueDate().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(nullSafe(p.getPolicyNumber()));
            row.createCell(col++).setCellValue(p.isPaid() ? "Yes" : "No");
            row.createCell(col++).setCellValue(p.getLastPaidDate() != null ? p.getLastPaidDate().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(p.isActive() ? "Yes" : "No");
            row.createCell(col).setCellValue(c != null ? nullSafe(c.getEmail()) : "");
        }
        autoSizeColumns(sheet, headers.length);
    }

    // ---------- Birthdays ----------

    public byte[] exportBirthdays() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = headerStyle(wb);
            writePolicyholderBirthdaySheet(wb, headerStyle);
            writeRelativeBirthdaySheet(wb, headerStyle);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writePolicyholderBirthdaySheet(XSSFWorkbook wb, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Policyholder Birthdays");
        String[] headers = {"Name", "Date of Birth", "Next Birthday", "Email", "Phone"};
        writeHeaderRow(sheet, headers, headerStyle);

        var entries = birthdayWishService.findAllWithDob();
        int rowNum = 1;
        for (var e : entries) {
            Customer c = e.customer();
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(c.getFullName()));
            row.createCell(col++).setCellValue(c.getDateOfBirth() != null ? c.getDateOfBirth().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(e.nextBirthday() != null ? e.nextBirthday().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(nullSafe(c.getEmail()));
            row.createCell(col).setCellValue(nullSafe(c.getPhone()));
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void writeRelativeBirthdaySheet(XSSFWorkbook wb, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Relative Birthdays");
        String[] headers = {"Name", "Relation", "Policyholder", "Date of Birth", "Next Birthday", "Email", "Phone"};
        writeHeaderRow(sheet, headers, headerStyle);

        var entries = birthdayWishService.findAllRelativesWithDob();
        int rowNum = 1;
        for (var e : entries) {
            CustomerRelative r = e.relative();
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(r.getFullName()));
            row.createCell(col++).setCellValue(nullSafe(r.getRelation()));
            row.createCell(col++).setCellValue(r.getCustomer() != null ? nullSafe(r.getCustomer().getFullName()) : "");
            row.createCell(col++).setCellValue(r.getDateOfBirth() != null ? r.getDateOfBirth().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(e.nextBirthday() != null ? e.nextBirthday().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(nullSafe(r.getEmail()));
            row.createCell(col).setCellValue(nullSafe(r.getPhone()));
        }
        autoSizeColumns(sheet, headers.length);
    }

    // ---------- Relatives ----------

    public byte[] exportRelatives() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = headerStyle(wb);
            writeRelativeSheet(wb, headerStyle);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeRelativeSheet(XSSFWorkbook wb, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Relatives");
        String[] headers = {
                "ID", "Name", "Relation", "Policyholder", "Date of Birth",
                "Email", "Phone", "Message Language"
        };
        writeHeaderRow(sheet, headers, headerStyle);

        List<CustomerRelative> relatives = relativeRepository.findAllWithCustomer();
        int rowNum = 1;
        for (CustomerRelative r : relatives) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(r.getId());
            row.createCell(col++).setCellValue(nullSafe(r.getFullName()));
            row.createCell(col++).setCellValue(nullSafe(r.getRelation()));
            row.createCell(col++).setCellValue(r.getCustomer() != null ? nullSafe(r.getCustomer().getFullName()) : "");
            row.createCell(col++).setCellValue(r.getDateOfBirth() != null ? r.getDateOfBirth().format(DATE_FMT) : "");
            row.createCell(col++).setCellValue(nullSafe(r.getEmail()));
            row.createCell(col++).setCellValue(nullSafe(r.getPhone()));
            row.createCell(col).setCellValue(nullSafe(r.getMessageLanguage()));
        }
        autoSizeColumns(sheet, headers.length);
    }


    // ---------- helpers ----------

    private String nullSafe(Object val) {
        return val == null ? "" : val.toString();
    }

    private void writeHeaderRow(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
    }

    private void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}