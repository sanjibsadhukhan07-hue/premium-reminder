package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.Role;
import com.premiumreminder.model.User;
import com.premiumreminder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class CustomerLoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final String ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Creates a login for the customer (username = their email) with a random temporary password.
     * Returns the plaintext password so the admin can share it once - it is never stored or shown again.
     */
    public String createLoginFor(Customer customer) {
        if (userRepository.findByUsername(customer.getEmail()).isPresent()) {
            throw new IllegalStateException("A login already exists for " + customer.getEmail());
        }
        String tempPassword = generatePassword();
        User user = new User();
        user.setUsername(customer.getEmail());
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setRole(Role.CUSTOMER);
        user.setCustomer(customer);
        userRepository.save(user);
        return tempPassword;
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
