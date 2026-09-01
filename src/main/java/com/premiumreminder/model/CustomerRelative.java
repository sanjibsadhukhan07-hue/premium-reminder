package com.premiumreminder.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_relative")
@Getter
@Setter
@NoArgsConstructor
public class CustomerRelative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @NotBlank
    private String fullName;

    // Free text ("Spouse", "Son", "Father"...) rather than an enum - households
    // don't fit a fixed list, and it's display-only, never branched on
    private String relation;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phone; // E.164, e.g. +9198XXXXXXXX - sent to directly via MSG91

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}