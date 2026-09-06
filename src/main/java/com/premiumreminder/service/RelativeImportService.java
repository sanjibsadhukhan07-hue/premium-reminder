package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.CustomerRelative;
import com.premiumreminder.repository.CustomerRelativeRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Bulk-imports family members from the "PROPOSER NAMES / FAMILY MEMBERS / RELATIONS /
 * DOB / CONTACT" sheet layout (e.g. Book2.xlsx "Sheet2") and attaches each row to an
 * existing Customer matched by full name. Sheets that don't have this column layout
 * (e.g. a plain customer birthday list) are skipped, not errored.
 *
 * Duplicate rows - same full name AND same phone under the same customer - are skipped
 * rather than inserted again. This check covers both relatives already in the DB from a
 * prior import and repeats within the same file/sheet being uploaded.
 */
@Service
@RequiredArgsConstructor
public class RelativeImportService {

    private final CustomerService customerService;
    private final CustomerRelativeRepository relativeRepository;

    private static final DataFormatter FORMATTER = new DataFormatter();
    private static final List<DateTimeFormatter> DATE_PATTERNS = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    @Getter
    public static class ImportResult {
        private int imported = 0;
        private int skippedSelf = 0;
        private int skippedIncompleteRow = 0;
        private int skippedDuplicate = 0;
        private int skippedNoMatch = 0;
        private final List<String> unmatchedProposers = new ArrayList<>();
        private final List<String> sheetsProcessed = new ArrayList<>();
        private final List<String> sheetsSkipped = new ArrayList<>();
    }

    private static class ColumnMap {
        int headerRow;
        int proposerCol = -1;
        int memberCol = -1;
        int relationCol = -1;
        int dobCol = -1;
        int phoneCol = -1;

        boolean isUsable() {
            return proposerCol >= 0 && memberCol >= 0;
        }
    }

    public ImportResult importFromExcel(MultipartFile file) throws Exception {
        ImportResult result = new ImportResult();

        // Load every customer once; matched in-memory by normalized full name rather
        // than round-tripping to the DB per row.
        Map<String, Customer> byName = new HashMap<>();
        for (Customer c : customerService.findAll()) {
            byName.putIfAbsent(normalize(c.getFullName()), c);
        }

        // Track existing + newly-added relatives per customer (lazily loaded) so we
        // catch duplicates both already in the DB and repeated within this same file.
        Map<Long, List<CustomerRelative>> existingByCustomer = new HashMap<>();

        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {

            for (Sheet sheet : wb) {
                ColumnMap cols = detectColumns(sheet);
                if (cols == null || !cols.isUsable()) {
                    result.sheetsSkipped.add(sheet.getSheetName());
                    continue;
                }
                result.sheetsProcessed.add(sheet.getSheetName());

                for (int r = cols.headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String proposerName = text(row, cols.proposerCol);
                    String memberName = text(row, cols.memberCol);
                    String relation = cols.relationCol >= 0 ? text(row, cols.relationCol) : null;

                    if (isBlank(proposerName) || isBlank(memberName)) {
                        result.skippedIncompleteRow++;
                        continue;
                    }
                    if (relation != null && relation.trim().equalsIgnoreCase("self")) {
                        result.skippedSelf++;
                        continue;
                    }

                    Customer customer = byName.get(normalize(proposerName));
                    if (customer == null) {
                        result.skippedNoMatch++;
                        result.unmatchedProposers.add(proposerName + " (sheet: " + sheet.getSheetName() + ")");
                        continue;
                    }

                    CustomerRelative relative = new CustomerRelative();
                    relative.setCustomer(customer);
                    relative.setFullName(titleCase(memberName));
                    relative.setRelation(relation != null && !relation.isBlank() ? titleCase(relation) : null);
                    relative.setMessageLanguage(customer.getMessageLanguage() != null
                            ? customer.getMessageLanguage() : "ENGLISH");

                    if (cols.dobCol >= 0) {
                        relative.setDateOfBirth(readDate(row.getCell(cols.dobCol)));
                    }
                    if (cols.phoneCol >= 0) {
                        relative.setPhone(readPhone(row.getCell(cols.phoneCol)));
                    }

                    List<CustomerRelative> existing = existingByCustomer.computeIfAbsent(
                            customer.getId(), id -> new ArrayList<>(relativeRepository.findByCustomerId(id)));

                    boolean isDuplicate = existing.stream().anyMatch(existingRel ->
                            normalize(existingRel.getFullName()).equals(normalize(relative.getFullName()))
                                    && Objects.equals(existingRel.getPhone(), relative.getPhone()));

                    if (isDuplicate) {
                        result.skippedDuplicate++;
                        continue;
                    }

                    relativeRepository.save(relative);
                    existing.add(relative);
                    result.imported++;
                }
            }
        }
        return result;
    }

    private ColumnMap detectColumns(Sheet sheet) {
        int scanLimit = Math.min(sheet.getLastRowNum(), 9);
        for (int r = 0; r <= scanLimit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            ColumnMap cols = new ColumnMap();
            cols.headerRow = r;
            for (Cell cell : row) {
                String v = FORMATTER.formatCellValue(cell).trim().toUpperCase(Locale.ROOT);
                if (v.isEmpty()) continue;
                int idx = cell.getColumnIndex();
                if (v.contains("PROPOSER") && v.contains("NAME")) {
                    cols.proposerCol = idx;
                } else if (v.contains("FAMILY") && v.contains("MEMBER")) {
                    cols.memberCol = idx;
                } else if (v.contains("RELATION")) {
                    cols.relationCol = idx;
                } else if (v.contains("D/M/Y") || v.contains("DATE OF BIRTH") || v.equals("DOB")) {
                    cols.dobCol = idx;
                } else if (v.contains("CONTACT") || v.contains("PHONE")) {
                    cols.phoneCol = idx;
                }
            }
            if (cols.isUsable()) {
                return cols;
            }
        }
        return null;
    }

    private LocalDate readDate(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                java.util.Date date = cell.getDateCellValue();
                return date == null ? null
                        : date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            }
            String text = FORMATTER.formatCellValue(cell).trim();
            if (text.isEmpty()) return null;
            for (DateTimeFormatter fmt : DATE_PATTERNS) {
                try {
                    return LocalDate.parse(text, fmt);
                } catch (Exception ignored) {
                    // try next pattern
                }
            }
        } catch (Exception ignored) {
            // leave DOB null rather than fail the whole row
        }
        return null;
    }

    private String readPhone(Cell cell) {
        if (cell == null) return null;
        String digits;
        if (cell.getCellType() == CellType.NUMERIC) {
            digits = String.valueOf((long) cell.getNumericCellValue());
        } else {
            digits = FORMATTER.formatCellValue(cell).replaceAll("[^0-9]", "");
        }
        if (digits.isEmpty()) return null;
        if (digits.length() == 10) return "+91" + digits;
        if (digits.length() == 12 && digits.startsWith("91")) return "+" + digits;
        return digits.startsWith("+") ? digits : "+" + digits;
    }

    private String text(Row row, int col) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        return cell == null ? null : FORMATTER.formatCellValue(cell).trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String titleCase(String s) {
        String[] words = s.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}