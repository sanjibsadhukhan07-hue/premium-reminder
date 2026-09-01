package com.premiumreminder.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.format.annotation.DateTimeFormat;

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

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    private String policyDocFileName;

    private String policyDocContentType;

    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Basic(fetch = FetchType.LAZY)
    private byte[] policyDocData;

    @NotNull
    private BigDecimal premiumAmount;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate nextDueDate;

    // How many days before nextDueDate reminders should start (default 30 = "1 month")
    private int reminderWindowDays = 30;

    // How often the premium repeats once paid & renewed: yearly, half-yearly, quarterly, or 3-yearly.
    // Replaces the old fixed-day renewalCycleDays field with calendar-correct date math
    // (see PremiumFrequency.nextDueDate).
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PremiumFrequency premiumFrequency = PremiumFrequency.YEARLY;

    private boolean paid = false;

    private LocalDate lastPaidDate;

    private LocalDate lastReminderSentDate;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean active = true;
}