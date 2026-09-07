package com.premiumreminder.service;

import java.awt.Color;

/**
 * Text placement/sizing/color for a birthday card template - stored per-template in the
 * DB so it can be tuned per uploaded image via the admin preview screen, instead of being
 * hardcoded for one specific template's dimensions and colors.
 */
public record CardTextPosition(
        int nameY,
        int dateY,
        int nameFontSizeMax,
        int nameFontSizeMin,
        int dateFontSize,
        int sideMargin,
        String textColorHex,
        String shadowColorHex
) {
    public static CardTextPosition defaults() {
        return new CardTextPosition(250, 330, 78, 36, 40, 60, "#3D4961", "#FFFFFF");
    }

    public Color textColor() {
        return Color.decode(textColorHex);
    }

    /** Shadow is always drawn semi-transparent regardless of the picked hex - only the hue is user-chosen. */
    public Color shadowColor() {
        Color base = Color.decode(shadowColorHex);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 160);
    }
}