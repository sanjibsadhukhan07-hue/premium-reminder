package com.premiumreminder.service;

import com.premiumreminder.model.Role;
import com.premiumreminder.model.User;
import com.premiumreminder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSettingsService {

    private final UserRepository userRepository;

    public List<User> findAdmins() {
        return userRepository.findByRole(Role.ADMIN);
    }

    /**
     * Numbers for every ADMIN user that has one set. Used by NotificationService to
     * fan out due-tomorrow / paid-confirmation alerts to all admins, not just one.
     */
    public List<String> findAdminWhatsAppNumbers() {
        return findAdmins().stream()
                .map(User::getWhatsappNumber)
                .filter(n -> n != null && !n.isBlank())
                .toList();
    }

    @Transactional
    public User updatePhone(Long userId, String phone) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (u.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Only ADMIN users can have a WhatsApp alert number.");
        }
        u.setWhatsappNumber(phone.trim());
        return userRepository.save(u);
    }
}