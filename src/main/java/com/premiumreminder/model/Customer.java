package com.premiumreminder.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    @Column(unique = true)
    private String email;

    @NotBlank
    private String phone; // E.164 format e.g. +91XXXXXXXXXX for MSG91

    @NotBlank
    @Column(unique = true)
    private String policyNumber;

    private String insurerName;

    @NotNull
    private BigDecimal premiumAmount;

    @NotNull
    private LocalDate nextDueDate;

    // How many days before nextDueDate reminders should start (default 30 = "1 month")
    private int reminderWindowDays = 30;

    // How often (in days) the premium repeats once paid & renewed (e.g. 365 for annual)
    private int renewalCycleDays = 365;

    private boolean paid = false;

    private LocalDate lastPaidDate;

    private LocalDate lastReminderSentDate;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean active = true;
}
