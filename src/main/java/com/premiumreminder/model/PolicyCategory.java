package com.premiumreminder.model;

/**
 * Broad category of an insurance policy. Kept as a fixed enum (rather than free text)
 * because the scheduler/reporting logic doesn't branch on it, but the admin UI groups
 * and filters by it. Add new constants here as new insurance lines are onboarded.
 */
public enum PolicyCategory {
    HEALTH("Health"),
    MOTOR("Motor"),
    LIFE("Life"),
    PERSONAL_ACCIDENT("Personal Accident"),
    TRAVEL("Travel"),
    TERM("Term"),
    OTHER("Other");

    private final String label;

    PolicyCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
