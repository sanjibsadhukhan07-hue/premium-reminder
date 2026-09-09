package com.premiumreminder.model;

import jakarta.persistence.*;
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
import java.time.ZoneId;

/**
 * A single insurance policy belonging to a Customer. Fields map onto the columns used
 * across your existing tracking sheets (HDFC Health, TATA Health, Motor, ...):
 *   NAME -> customer.fullName, OFFICE TAG -> customer.officeTag,
 *   COMPANY -> insurerName, PLAN TYPE -> planType, POLICY TYPE -> policyType,
 *   S.A./IDV -> sumAssured, PREMIUM -> premiumAmount,
 *   STARTING DATE -> startDate, RENUWAL DATE -> nextDueDate, POLICY NO. -> policyNumber.
 * Category-specific extras (health check-up note, vehicle registration number) are kept
 * as separate optional fields rather than overloading one column, since the source
 * sheets used the same column header for different things per category.
 */
@Entity
@Table(name = "policy")
@Getter
@Setter
@NoArgsConstructor
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No @NotNull here deliberately: this is set server-side from the URL path
    // (see AdminController/PolicyService), never bound from the policy form itself.
    // A bean-validation @NotNull on this field would fail on every single save,
    // since the submitted form has no "customer" field to bind - and would then
    // NPE the template on re-render (policy.customer.fullName) since customer
    // would still be null at that point. @JoinColumn(nullable = false) below still
    // enforces the real DB-level constraint.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    private String policyHolderName;

    @NotNull
    @Enumerated(EnumType.STRING)
    private PolicyCategory category = PolicyCategory.HEALTH;

    @NotBlank
    @Column(unique = true)
    private String policyNumber;

    private String insurerName; // e.g. HDFC, TATA AIG, NIC

    private String planType;    // e.g. FAMILY, INDIVIDUAL, SINGLE

    private String policyType;  // e.g. SECURE, RESTORE, PREMIER, PACKAGE

    private BigDecimal sumAssured; // S.A. / IDV

    // Health-only free text note, e.g. "2.5K EVERY YEAR" health check-up allowance
    private String healthCheckupNote;

    // Motor-only vehicle registration number, e.g. "WB-16-AV-1309"
    private String vehicleRegistrationNo;

    private String policyDocFileName;
    private String policyDocContentType;

    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Basic(fetch = FetchType.LAZY)
    private byte[] policyDocData;

    @NotNull
    private BigDecimal premiumAmount;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate nextDueDate;

    private LocalDate previousDueDate;

    // How many days before AND after nextDueDate reminders should fire (default 30 each side)
    private int reminderWindowDays = 30;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PremiumFrequency premiumFrequency = PremiumFrequency.YEARLY;

    private boolean paid = false;

    private LocalDate lastPaidDate;

    private LocalDate lastReminderSentDate;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

    private boolean active = true;
}