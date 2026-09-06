package com.premiumreminder.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A policyholder (a person), independent of any single policy.
 *
 * ENTITY REDESIGN NOTE: the old Customer entity carried exactly one policy's worth of
 * fields (policyNumber, premiumAmount, nextDueDate, ...). Your real data shows the same
 * person holding several policies at once, often with different insurers (e.g. AVIJIT
 * KOLEY has an HDFC health policy and a separate TATA AIG personal-accident policy).
 * Policy-specific data now lives on Policy (see Policy.java), one-to-many from here.
 */
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

    // Optional - your source sheets leave this blank for most people. Required only if
    // you want the customer to have a portal login or to receive the email channel of
    // reminders/birthday wishes (WhatsApp still goes out regardless).
    @Email
    @Column(unique = true)
    private String email;

    @NotBlank
    private String phone; // E.164 format e.g. +91XXXXXXXXXX - the primary matching key

    private String whatsappNumber;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    // Free-text office/agent tag carried over from the source sheets (e.g. "PAMPA", "FCA",
    // "ANUPAM", "SELF") - which office/agent brought this customer in. Display-only.
    private String officeTag;

    // "ENGLISH", "HINDI", or "BENGALI" - drives which pre-approved WhatsApp template
    // language variant is used when sending this customer reminders/wishes.
    private String messageLanguage = "ENGLISH";

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean active = true;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Policy> policies = new ArrayList<>();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerRelative> relatives = new ArrayList<>();

    /**
     * Earliest upcoming/overdue due date across this customer's active, unpaid,
     * reminder-eligible policies. TRAVEL policies are excluded - they're never
     * reminded, so they shouldn't drive the dashboard's due-date sort/status either.
     * Null if none.
     */
    @Transient
    public LocalDate getEarliestDueDate() {
        return policies.stream()
                .filter(Policy::isActive)
                .filter(p -> !p.isPaid())
                .filter(p -> p.getCategory() != PolicyCategory.TRAVEL)
                .map(Policy::getNextDueDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
    }
}