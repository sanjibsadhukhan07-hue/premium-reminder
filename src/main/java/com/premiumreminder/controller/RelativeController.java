package com.premiumreminder.controller;

import com.premiumreminder.model.CustomerRelative;
import com.premiumreminder.repository.CustomerRelativeRepository;
import com.premiumreminder.service.CustomerService;
import com.premiumreminder.service.RelativeImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/relatives")
@RequiredArgsConstructor
public class RelativeController {

    private final CustomerRelativeRepository relativeRepository;
    private final CustomerService customerService;
    private final RelativeImportService relativeImportService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("relatives", relativeRepository.findAllWithCustomer());
        return "admin/relatives-list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId, Model model) {
        CustomerRelative relative = new CustomerRelative();
        if (customerId != null) {
            relative.setCustomer(customerService.findById(customerId));
        }
        model.addAttribute("relative", relative);
        model.addAttribute("customers", customerService.findAll());
        return "admin/relative-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        CustomerRelative relative = relativeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Relative not found: " + id));
        model.addAttribute("relative", relative);
        model.addAttribute("customers", customerService.findAll());
        return "admin/relative-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute CustomerRelative formRelative, @RequestParam Long customerId,
                       RedirectAttributes redirectAttributes) {
        if (formRelative.getId() == null &&
                relativeRepository.existsByCustomerIdAndFullNameIgnoreCaseAndPhone(
                        customerId, formRelative.getFullName(), formRelative.getPhone())) {
            redirectAttributes.addFlashAttribute("importError",
                    "Duplicate relative: " + formRelative.getFullName() + " with this phone number already exists for this policyholder.");
            return "redirect:/admin/relatives";
        }
        formRelative.setCustomer(customerService.findById(customerId));
        relativeRepository.save(formRelative);
        return "redirect:/admin/relatives";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        relativeRepository.deleteById(id);
        return "redirect:/admin/relatives";
    }

    @PostMapping("/import")
    public String importRelatives(@RequestParam("file") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("importError", "Please choose an Excel (.xlsx) file to upload.");
            return "redirect:/admin/relatives";
        }
        try {
            RelativeImportService.ImportResult result = relativeImportService.importFromExcel(file);
            redirectAttributes.addFlashAttribute("relativeImportResult", result);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("importError", "Import failed: " + e.getMessage());
        }
        return "redirect:/admin/relatives";
    }

    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam(value = "ids", required = false) List<Long> ids,
                             RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("importError", "No relatives were selected.");
            return "redirect:/admin/relatives";
        }
        relativeRepository.deleteAllById(ids);
        redirectAttributes.addFlashAttribute("wishSent", "Deleted " + ids.size() + " relative(s).");
        return "redirect:/admin/relatives";
    }
}
