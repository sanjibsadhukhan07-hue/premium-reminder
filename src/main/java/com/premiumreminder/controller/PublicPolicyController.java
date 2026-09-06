package com.premiumreminder.controller;

import com.premiumreminder.service.PolicyPaymentLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class PublicPolicyController {

    private final PolicyPaymentLinkService paymentLinkService;

    // policyId in the path is cosmetic only - the token alone determines which
    // policy actually gets marked paid (see PolicyPaymentLinkService).
    @GetMapping("/policies/{policyId}/mark-paid/{token}")
    public String landing(@PathVariable Long policyId, @PathVariable String token, Model model) {
        var result = paymentLinkService.preview(token);
        model.addAttribute("status", result.status());
        model.addAttribute("policy", result.policy());
        model.addAttribute("token", token);
        return "public/mark-paid-landing";
    }

    @PostMapping("/policies/{policyId}/mark-paid/{token}")
    public String confirm(@PathVariable Long policyId, @PathVariable String token, Model model) {
        var result = paymentLinkService.confirm(token);
        model.addAttribute("status", result.status());
        model.addAttribute("policy", result.policy());
        return "public/mark-paid-result";
    }
}