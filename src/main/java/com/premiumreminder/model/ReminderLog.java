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
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    private boolean success;

    @Column(length = 1000)
    private String detail;

    private LocalDateTime sentAt = LocalDateTime.now();

    public enum Channel {
        EMAIL, SMS, WHATSAPP
    }
}
