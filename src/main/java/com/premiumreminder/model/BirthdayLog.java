package com.premiumreminder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "birthday_log")
@Getter
@Setter
@NoArgsConstructor
public class BirthdayLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;
    private String customerName;
    private String channel; // "EMAIL" or "WHATSAPP"
    private boolean success;
    private String message; // error detail, or confirmation text
    private Long relativeId;   // null when the wish went to the policyholder themselves
    private String relation;   // e.g. "Spouse" - null when relativeId is null

    private LocalDateTime sentAt = LocalDateTime.now();
}