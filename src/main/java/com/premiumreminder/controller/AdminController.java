package com.premiumreminder.controller;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.Policy;
import com.premiumreminder.repository.BirthdayLogRepository;
import com.premiumreminder.repository.ReminderLogRepository;
import com.premiumreminder.scheduler.PremiumReminderScheduler;
import com.premiumreminder.service.*;
import com.premiumreminder.service.PolicyService.ExcelImportResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 20, 30, 50);
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CustomerService customerService;
    private final PolicyService policyService;
    private final ReminderLogRepository reminderLogRepository;
    private final PremiumReminderScheduler scheduler;
    private final CustomerLoginService customerLoginService;
    private final BirthdayWishService birthdayWishService;
    private final BirthdayLogRepository birthdayLogRepository;
    private final ExportService exportService;   // add to constructor-injected fields
    private final AdminSettingsService adminSettingsService;

    /**
     * Dashboard: policyholders sorted by their nearest upcoming/overdue premium due
     * date across all their ACTIVE policies (paid or not) - soonest/most overdue
     * first, customers with no active policy sorted last. This mirrors
     * Customer::getNearestActivePolicy, which is also what the template uses to
     * render the "Nearest Due Date" / "Status" columns, so the sort order always
     * matches what's on screen. Search (q) filters by name/policy number/email/phone.
     */

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(name = "q", required = false) String q,
                            @RequestParam(name = "page", defaultValue = "0") int page,
                            @RequestParam(name = "size", defaultValue = "20") int size,
                            @RequestParam(name = "fragment", required = false) boolean fragment,
                            Model model) {
        int pageSize = ALLOWED_PAGE_SIZES.contains(size) ? size : DEFAULT_PAGE_SIZE;

        List<Customer> allCustomers = customerService.search(q);
        allCustomers.sort(Comparator.comparing(
                (Customer c) -> {
                    Policy nearest = c.getNearestActivePolicy();
                    return nearest != null ? nearest.getNextDueDate() : (LocalDate) null;
                },
                Comparator.nullsLast(Comparator.naturalOrder())));

        int totalCustomers = allCustomers.size();
        int totalPages = (int) Math.ceil((double) totalCustomers / pageSize);
        int currentPage = Math.max(0, Math.min(page, Math.max(totalPages - 1, 0)));
        int fromIndex = currentPage * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalCustomers);
        List<Customer> pageCustomers = fromIndex < toIndex ? allCustomers.subList(fromIndex, toIndex) : List.of();

        model.addAttribute("customers", pageCustomers);
        model.addAttribute("dueTodayCount", policyService.findDueForReminderToday().size());
        model.addAttribute("q", q);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCustomers", totalCustomers);
        model.addAttribute("pageSize", pageSize);

        return fragment ? "admin/dashboard :: resultsContainer" : "admin/dashboard";
    }

    @GetMapping("/customers/new")
    public String newCustomerForm(Model model) {
        model.addAttribute("customer", new Customer());
        return "admin/customer-form";
    }

    @PostMapping("/customers/import")
    public String importCustomers(@RequestParam("file") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("importError", "Please choose an Excel (.xlsx) file to upload.");
            return "redirect:/admin/dashboard";
        }
        try {
            ExcelImportResult result = policyService.importFromExcel(file);
            redirectAttributes.addFlashAttribute("importResult", result);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("importError", "Import failed: " + e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/customers/{id}/edit")
    public String editCustomerForm(@PathVariable Long id, Model model) {
        model.addAttribute("customer", customerService.findById(id));
        return "admin/customer-form";
    }

    @PostMapping("/customers/save")
    public String saveCustomer(@Valid @ModelAttribute("customer") Customer customer, BindingResult result,
                               RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/customer-form";
        }
        try {
            Customer saved = customerService.save(customer);
            redirectAttributes.addFlashAttribute("customerSaved", "Customer saved successfully.");
            return "redirect:/admin/customers/" + saved.getId() + "/edit";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("importError", e.getMessage());
            return customer.getId() != null
                    ? "redirect:/admin/customers/" + customer.getId() + "/edit"
                    : "redirect:/admin/customers/new";
        }
    }

    @PostMapping("/customers/{id}/delete")
    public String deleteCustomer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String name = customerService.delete(id);
        redirectAttributes.addFlashAttribute("customerDeleted", "Customer \"" + name + "\" deleted successfully.");
        return "redirect:/admin/dashboard";
    }

    // --- Policies (a customer can have several) ---------------------------------------

    @GetMapping("/customers/{customerId}/policies/new")
    public String newPolicyForm(@PathVariable Long customerId, Model model) {
        Policy policy = new Policy();
        policy.setCustomer(customerService.findById(customerId));
        model.addAttribute("policy", policy);
        return "admin/policy-form";
    }

    @GetMapping("/policies/new")
    public String newPolicyFormGeneral(Model model) {
        model.addAttribute("policy", new Policy());
        model.addAttribute("customers", sortedCustomers()); // adjust if your "list all" method has a different name
        return "admin/policy-form";
    }

    @GetMapping("/policies/{id}/edit")
    public String editPolicyForm(@PathVariable Long id, Model model) {
        model.addAttribute("policy", policyService.findById(id));
        return "admin/policy-form";
    }

    @PostMapping("/policies/save")
    public String savePolicy(@RequestParam Long customerId,
                             @Valid @ModelAttribute("policy") Policy policy,
                             BindingResult result,
                             @RequestParam(value = "policyDoc", required = false) MultipartFile policyDoc,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (result.hasErrors()) {
            policy.setCustomer(customerService.findById(customerId));
            model.addAttribute("customers", sortedCustomers());
            return "admin/policy-form";
        }
        Policy saved = policyService.save(customerId, policy);

        if (policyDoc != null && !policyDoc.isEmpty()) {
            try {
                policyService.savePolicyDoc(saved.getId(), policyDoc);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute(
                        "importError", "Policy saved, but document upload failed: " + e.getMessage());
            }
        }
        return "redirect:/admin/customers/" + customerId + "/edit";
    }

    @PostMapping("/policies/{id}/delete")
    public String deletePolicy(@PathVariable Long id) {
        Long customerId = policyService.findById(id).getCustomer().getId();
        policyService.delete(id);
        return "redirect:/admin/customers/" + customerId + "/edit";
    }

    @PostMapping("/policies/{id}/mark-paid")
    public String markPaid(@PathVariable Long id, @RequestParam(required = false) String from) {
        policyService.markAlreadyPaid(id);
        return "redirect:" + (from != null ? from : "/admin/dashboard");
    }

    @PostMapping("/policies/{id}/unmark-paid")
    public String unmarkPaid(@PathVariable Long id, @RequestParam(required = false) String from) {
        policyService.unmarkPaid(id);
        return "redirect:" + (from != null ? from : "/admin/dashboard");
    }

    /**
     * Policies grouped by insurer, mirroring the tabbed layout of the source workbook
     * (one tab per insurer, e.g. HDFC HEALTH INSURANCE / TATA HEALTH INSURANCE / MOTOR
     * INSURANCE / ...). Each tab shows the full policy + policyholder detail in one row.
     */
    @GetMapping("/policies")
    public String policiesByInsurer(Model model) {
        List<Policy> all = policyService.findAllSortedByNextDueDate();
        java.util.Map<String, List<Policy>> byInsurer = new java.util.LinkedHashMap<>();
        all.stream()
                .sorted(Comparator.comparing(p -> p.getInsurerName() == null ? "" : p.getInsurerName()))
                .forEach(p -> {
                    String key = (p.getInsurerName() == null || p.getInsurerName().isBlank())
                            ? "Unspecified" : p.getInsurerName();
                    byInsurer.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(p);
                });
        model.addAttribute("policiesByInsurer", byInsurer);
        return "admin/policies-by-insurer";
    }

    @GetMapping("/policies/{id}/policy-doc")
    public org.springframework.http.ResponseEntity<byte[]> viewPolicyDoc(@PathVariable Long id) {
        Policy policy = policyService.findById(id);
        byte[] data = policy.getPolicyDocData();
        if (data == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        org.springframework.http.MediaType mediaType = policy.getPolicyDocContentType() != null
                ? org.springframework.http.MediaType.parseMediaType(policy.getPolicyDocContentType())
                : org.springframework.http.MediaType.APPLICATION_PDF;
        String filename = policy.getPolicyDocFileName() != null
                ? policy.getPolicyDocFileName() : "policy-document.pdf";

        return org.springframework.http.ResponseEntity.ok()
                .contentType(mediaType)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(data);
    }

    @PostMapping("/customers/{id}/create-login")
    public String createLogin(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var customer = customerService.findById(id);
        try {
            String tempPassword = customerLoginService.createLoginFor(customer);
            redirectAttributes.addFlashAttribute("loginCreatedFor", customer.getEmail());
            redirectAttributes.addFlashAttribute("tempPassword", tempPassword);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("loginError", e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/reminder-logs")
    public String reminderLogs(Model model) {
        model.addAttribute("logs", reminderLogRepository.findTop200ByOrderBySentAtDesc());
        return "admin/reminder-logs";
    }

    @PostMapping("/run-reminders-now")
    public String runNow() {
        scheduler.runDailyRemindersNow();
        return "redirect:/admin/reminder-logs";
    }

    @GetMapping("/birthdays")
    public String birthdays(Model model) {
        model.addAttribute("entries", birthdayWishService.findAllWithDob());
        model.addAttribute("birthdaysToday", birthdayWishService.findBirthdaysToday());
        model.addAttribute("relativeEntries", birthdayWishService.findAllRelativesWithDob());
        model.addAttribute("relativeBirthdaysToday", birthdayWishService.findRelativeBirthdaysToday());
        return "admin/birthdays";
    }

    @PostMapping("/birthdays/{id}/send")
    public String sendBirthdayWish(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            birthdayWishService.sendWishNow(id);
            redirectAttributes.addFlashAttribute("wishSent", "Birthday wish sent.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("wishError", "Failed to send: " + e.getMessage());
        }
        return "redirect:/admin/birthday-logs";
    }

    @PostMapping("/run-birthday-wishes-now")
    public String runBirthdayWishesNow(RedirectAttributes redirectAttributes) {
        int count = birthdayWishService.runDailyBirthdayWishes();
        redirectAttributes.addFlashAttribute("wishSent", "Sent " + count + " birthday wish(es).");
        return "redirect:/admin/birthday-logs";
    }

    @PostMapping("/relatives/{id}/send-wish")
    public String sendRelativeBirthdayWish(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            birthdayWishService.sendRelativeWishNow(id);
            redirectAttributes.addFlashAttribute("wishSent", "Birthday wish sent.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("wishError", "Failed to send: " + e.getMessage());
        }
        return "redirect:/admin/birthday-logs";
    }

    @PostMapping("/policies/bulk-delete")
    public String bulkDeletePolicies(@RequestParam(value = "ids", required = false) List<Long> ids,
                                     RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("importError", "No policies were selected.");
            return "redirect:/admin/policies";
        }
        policyService.deleteAll(ids);
        redirectAttributes.addFlashAttribute("importResult", "Deleted " + ids.size() + " polic" + (ids.size() == 1 ? "y" : "ies") + ".");
        return "redirect:/admin/policies";
    }

    @PostMapping("/customers/bulk-delete")
    public String bulkDeleteCustomers(@RequestParam(value = "ids", required = false) List<Long> ids,
                                      RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("importError", "No customers were selected.");
            return "redirect:/admin/dashboard";
        }
        customerService.deleteAll(ids);
        redirectAttributes.addFlashAttribute("customerBulkDeleted",
                "Deleted " + ids.size() + " customer" + (ids.size() == 1 ? "" : "s") + " (and their policies/relatives).");
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/birthday-logs")
    public String birthdayLogs(Model model) {
        model.addAttribute("logs", birthdayLogRepository.findTop200ByOrderBySentAtDesc());
        return "admin/birthday-logs";
    }

    @GetMapping("/export/customers-policies")
    public org.springframework.http.ResponseEntity<byte[]> exportCustomersAndPolicies() throws java.io.IOException {
        byte[] data = exportService.exportCustomersAndPolicies();
        String filename = "customers-and-policies-" + java.time.LocalDate.now() + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType
                        .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    @GetMapping("/export/birthdays")
    public org.springframework.http.ResponseEntity<byte[]> exportBirthdays() throws java.io.IOException {
        byte[] data = exportService.exportBirthdays();
        String filename = "birthdays-" + java.time.LocalDate.now() + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType
                        .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    @GetMapping("/settings")
    public String settingsForm(Model model) {
        model.addAttribute("admins", adminSettingsService.findAdmins());
        return "admin/settings";
    }

    @PostMapping("/settings/update-phone")
    public String updateAdminPhone(@RequestParam Long userId,
                                   @RequestParam String whatsappNumber,
                                   RedirectAttributes redirectAttributes) {
        adminSettingsService.updatePhone(userId, whatsappNumber);
        redirectAttributes.addFlashAttribute("settingsSaved", "WhatsApp number updated.");
        return "redirect:/admin/settings";
    }

    private List<Customer> sortedCustomers() {
        List<Customer> customers = customerService.search(null);
        customers.sort(Comparator.comparing(c -> c.getFullName() == null ? "" : c.getFullName().toLowerCase()));
        return customers;
    }
}