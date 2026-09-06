package com.premiumreminder.model;

import java.time.LocalDate;

/**
 * Premium renewal frequency. Uses calendar-based date math so e.g. yearly renewals
 * land on the same calendar date next year regardless of leap years, and month-based
 * cycles correctly handle months of different lengths.
 */
public enum PremiumFrequency {

    YEARLY("Yearly") {
        @Override
        public LocalDate nextDueDate(LocalDate from) {
            return from.plusYears(1);
        }
    },
    HALF_YEARLY("Half-Yearly") {
        @Override
        public LocalDate nextDueDate(LocalDate from) {
            return from.plusMonths(6);
        }
    },
    QUARTERLY("Quarterly") {
        @Override
        public LocalDate nextDueDate(LocalDate from) {
            return from.plusMonths(3);
        }
    },
    THREE_YEARLY("3-Yearly") {
        @Override
        public LocalDate nextDueDate(LocalDate from) {
            return from.plusYears(3);
        }
    };

    private final String label;

    PremiumFrequency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public abstract LocalDate nextDueDate(LocalDate from);
}
