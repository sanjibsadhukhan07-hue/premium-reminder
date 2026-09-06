package com.premiumreminder.service;

import com.premiumreminder.model.User;
import com.premiumreminder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Changes the password for the given username after verifying the current one.
     * Clears mustChangePassword so the person isn't bounced back to this form on
     * their next request.
     */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }
}
