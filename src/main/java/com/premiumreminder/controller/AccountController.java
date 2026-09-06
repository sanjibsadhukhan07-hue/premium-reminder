package com.premiumreminder.controller;

import com.premiumreminder.repository.UserRepository;
import com.premiumreminder.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final UserRepository userRepository;

    @GetMapping("/account/change-password")
    public String form(Authentication authentication, Model model) {
        boolean forced = userRepository.findByUsername(authentication.getName())
                .map(u -> u.isMustChangePassword())
                .orElse(false);
        model.addAttribute("forced", forced);
        return "account/change-password";
    }

    @PostMapping("/account/change-password")
    public String submit(Authentication authentication,
                         @RequestParam String currentPassword,
                         @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("changePasswordError", "New password and confirmation don't match.");
            return "redirect:/account/change-password";
        }
        try {
            accountService.changePassword(authentication.getName(), currentPassword, newPassword);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("changePasswordError", e.getMessage());
            return "redirect:/account/change-password";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        redirectAttributes.addFlashAttribute("changePasswordSuccess", "Password updated.");
        return isAdmin ? "redirect:/admin/dashboard" : "redirect:/customer/dashboard";
    }
}
