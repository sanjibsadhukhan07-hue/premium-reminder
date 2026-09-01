package com.premiumreminder.model;

import java.time.LocalDate;

/**
 * Premium renewal frequency. Replaces the old renewalCycleDays (fixed day-count)
 * approach with calendar-based date math, so e.g. yearly renewals land on the
 * same calendar date next year regardless of leap years, and month-based cycles
 * correctly handle months of different lengths.
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

    /**
     * Computes the next due date from a given date, according to this frequency.
     * Uses java.time's calendar-aware plusYears/plusMonths, which correctly
     * handles Feb 29 and end-of-month edge cases (e.g. Jan 31 + 1 month -> Feb 28/29).
     */
    public abstract LocalDate nextDueDate(LocalDate from);
}