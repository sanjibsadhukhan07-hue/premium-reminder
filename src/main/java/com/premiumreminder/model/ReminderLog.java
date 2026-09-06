package com.premiumreminder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_log")
@Getter
@Setter
@NoArgsConstructor
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    // Denormalized so log rows stay readable even if a policy is later deleted
    private String customerName;
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    private boolean success;

    @Column(length = 1000)
    private String detail;

    // The actual phone number the message was sent to, when channel = WHATSAPP (the
    // customer's effective WhatsApp number - see NotificationService.effectiveWhatsAppNumber).
    // Null for EMAIL rows.
    private String phone;

    private LocalDateTime sentAt = LocalDateTime.now();

    // SMS removed per requirement - WhatsApp and Email only
    public enum Channel {
        EMAIL, WHATSAPP
    }
}