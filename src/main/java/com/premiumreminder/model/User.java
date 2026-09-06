package com.premiumreminder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt hashed

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Set when role == CUSTOMER, links the login to the customer record
    @OneToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private boolean enabled = true;

    // True for every newly-created login (the seeded admin account, or a customer
    // login created via CustomerLoginService) since they start on a temporary/default
    // password. Cleared the first time the user successfully changes their password.
    private boolean mustChangePassword = true;
}
