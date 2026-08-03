package com.premiumreminder.controller;

import com.premiumreminder.model.Customer;
import com.premiumreminder.repository.ReminderLogRepository;
import com.premiumreminder.scheduler.PremiumReminderScheduler;
import com.premiumreminder.service.CustomerLoginService;
import com.premiumreminder.service.CustomerService;
import com.premiumreminder.service.CustomerService.CsvImportResult;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CustomerService customerService;
    private final ReminderLogRepository reminderLogRepository;
    private final PremiumReminderScheduler scheduler;
    private final CustomerLoginService customerLoginService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("dueToday", customerService.findDueForReminderToday());
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

    @PostMapping("/customers/save")
    public String saveCustomer(@Valid @ModelAttribute("customer") Customer customer, BindingResult result) {
        if (result.hasErrors()) {
            return "admin/customer-form";
        }
        customerService.save(customer);
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

    // Lets an admin trigger today's reminder batch on demand, e.g. for testing
    @PostMapping("/run-reminders-now")
    public String runNow() {
        scheduler.runDailyReminders();
        return "redirect:/admin/reminder-logs";
    }
}
