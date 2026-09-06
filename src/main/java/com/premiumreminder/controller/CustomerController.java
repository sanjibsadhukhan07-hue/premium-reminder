package com.premiumreminder.controller;

import com.premiumreminder.repository.UserRepository;
import com.premiumreminder.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class CustomerController {

    private final UserRepository userRepository;
    private final PolicyService policyService;

    @GetMapping("/customer/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();
        model.addAttribute("customer", user.getCustomer());
        if (user.getCustomer() != null) {
            model.addAttribute("policies", user.getCustomer().getPolicies());
        }
        return "customer/dashboard";
    }
}
