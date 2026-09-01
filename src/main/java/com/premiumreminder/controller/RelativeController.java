package com.premiumreminder.controller;

import com.premiumreminder.model.CustomerRelative;
import com.premiumreminder.repository.CustomerRelativeRepository;
import com.premiumreminder.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/relatives")
@RequiredArgsConstructor
public class RelativeController {

    private final CustomerRelativeRepository relativeRepository;
    private final CustomerService customerService;

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
    public String save(@ModelAttribute CustomerRelative formRelative, @RequestParam Long customerId) {
        formRelative.setCustomer(customerService.findById(customerId));
        // createdAt is @Column(updatable = false), so Hibernate skips it on UPDATE even
        // though the unbound form object arrives with a fresh LocalDateTime.now() default
        relativeRepository.save(formRelative);
        return "redirect:/admin/relatives";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        relativeRepository.deleteById(id);
        return "redirect:/admin/relatives";
    }
}