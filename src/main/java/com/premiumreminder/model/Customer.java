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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A policyholder (a person), independent of any single policy.
 * <p>
 * ENTITY REDESIGN NOTE: the old Customer entity carried exactly one policy's worth of
 * fields (policyNumber, premiumAmount, nextDueDate, ...). Your real data shows the same
 * person holding several policies at once, often with different insurers (e.g. AVIJIT
 * KOLEY has an HDFC health policy and a separate TATA AIG personal-accident policy).
 * Policy-specific data now lives on Policy (see Policy.java), one-to-many from here.
 * <p>
 * officeTag (which office/agent brought a policy in) has similarly moved to Policy -
 * a customer can have policies sourced through different offices/agents, so it's not
 * a single fixed attribute of the person.
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

    // "ENGLISH", "HINDI", or "BENGALI" - drives which pre-approved WhatsApp template
    // language variant is used when sending this customer reminders/wishes.
    private String messageLanguage = "ENGLISH";

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

    private boolean active = true;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Policy> policies = new ArrayList<>();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerRelative> relatives = new ArrayList<>();

    /**
     * Earliest upcoming/overdue due date across this customer's active, UNPAID,
     * reminder-eligible policies. TRAVEL policies are excluded - they're never
     * reminded, so they shouldn't drive reminder scheduling either. Null if none.
     * <p>
     * This intentionally only looks at unpaid policies - it backs the reminder
     * scheduler's "who's due" logic, not the dashboard display. For the dashboard's
     * due-date column and status badge, use {@link #getNearestActivePolicy()} instead,
     * since a customer whose policies are all currently paid should still show their
     * real status rather than being treated as having no policy at all.
     */
    @Transient
    public LocalDate getEarliestDueDate() {
        return policies.stream()
                .filter(Policy::isActive)
                .filter(p -> !p.isPaid())
                .filter(p -> p.getCategory() != PolicyCategory.TRAVEL)
                .map(Policy::getNextDueDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * The active, non-TRAVEL policy with the nearest due date, regardless of paid
     * status. This is what the dashboard uses to decide what to show in the "Nearest
     * Due Date" / "Status" / "Office Tag" columns: as long as the customer has at
     * least one active policy, its actual paid/unpaid state, date, and office tag
     * drive the row. Only when there is no such policy at all does the dashboard fall
     * back to "No active policy". Null if the customer has no active, non-TRAVEL
     * policies.
     */
    @Transient
    public Policy getNearestActivePolicy() {
        return policies.stream()
                .filter(Policy::isActive)
                .filter(p -> p.getCategory() != PolicyCategory.TRAVEL)
                .filter(p -> p.getNextDueDate() != null)
                .min(Comparator.comparing(Policy::getNextDueDate))
                .orElse(null);
    }
}