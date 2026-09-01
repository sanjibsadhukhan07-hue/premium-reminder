package com.premiumreminder.controller;

import com.premiumreminder.model.Customer;
import com.premiumreminder.repository.ReminderLogRepository;
import com.premiumreminder.scheduler.PremiumReminderScheduler;
import com.premiumreminder.service.BirthdayWishService;
import com.premiumreminder.service.CustomerLoginService;
import com.premiumreminder.service.CustomerService;
import com.premiumreminder.service.CustomerService.CsvImportResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CustomerService customerService;
    private final ReminderLogRepository reminderLogRepository;
    private final PremiumReminderScheduler scheduler;
    private final CustomerLoginService customerLoginService;
    private final BirthdayWishService birthdayWishService;

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(name = "q", required = false) String q, Model model) {
        model.addAttribute("customers", customerService.search(q));
        model.addAttribute("dueToday", customerService.findDueForReminderToday());
        model.addAttribute("q", q);
        return "admin/dashboard";
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
            redirectAttributes.addFlashAttribute("importError", "Please choose a CSV file to upload.");
            return "redirect:/admin/dashboard";
        }
        try {
            CsvImportResult result = customerService.importFromCsv(file);
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

    // Single form now handles create/update AND an optional policy doc upload
    // in the same submit (multipart), so the doc no longer requires a second
    // "save first, then upload" step.
    @PostMapping("/customers/save")
    public String saveCustomer(@Valid @ModelAttribute("customer") Customer customer,
                               BindingResult result,
                               @RequestParam(value = "policyDoc", required = false) MultipartFile policyDoc,
                               RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/customer-form";
        }

        Customer saved = customerService.save(customer);

        if (policyDoc != null && !policyDoc.isEmpty()) {
            try {
                customerService.savePolicyDoc(saved.getId(), policyDoc);
            } catch (IllegalArgumentException | IOException e) {
                // Customer data is already saved; just surface the doc-upload problem.
                redirectAttributes.addFlashAttribute(
                        "importError", "Customer saved, but policy document upload failed: " + e.getMessage());
            }
        }

        return "redirect:/admin/dashboard";
    }

    @PostMapping("/customers/{id}/delete")
    public String deleteCustomer(@PathVariable Long id) {
        customerService.delete(id);
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/customers/{id}/mark-paid")
    public String markPaid(@PathVariable Long id) {
        customerService.markPaid(id);
        return "redirect:/admin/dashboard";
    }

    // Was referenced by the template but never actually implemented.
    @GetMapping("/customers/{id}/policy-doc")
    public ResponseEntity<byte[]> viewPolicyDoc(@PathVariable Long id) {
        Customer customer = customerService.findById(id);
        byte[] data = customer.getPolicyDocData();
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = customer.getPolicyDocContentType() != null
                ? MediaType.parseMediaType(customer.getPolicyDocContentType())
                : MediaType.APPLICATION_PDF;
        String filename = customer.getPolicyDocFileName() != null
                ? customer.getPolicyDocFileName() : "policy-document.pdf";

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
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
        scheduler.runDailyReminders();
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
        return "redirect:/admin/birthdays";
    }

    @PostMapping("/run-birthday-wishes-now")
    public String runBirthdayWishesNow(RedirectAttributes redirectAttributes) {
        int count = birthdayWishService.runDailyBirthdayWishes();
        redirectAttributes.addFlashAttribute("wishSent", "Sent " + count + " birthday wish(es).");
        return "redirect:/admin/birthdays";
    }

    @PostMapping("/relatives/{id}/send-wish")
    public String sendRelativeBirthdayWish(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            birthdayWishService.sendRelativeWishNow(id);
            redirectAttributes.addFlashAttribute("wishSent", "Birthday wish sent.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("wishError", "Failed to send: " + e.getMessage());
        }
        return "redirect:/admin/birthdays";
    }
}