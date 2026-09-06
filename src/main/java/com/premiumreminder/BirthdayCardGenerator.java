package com.premiumreminder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates an ornate "Happy Birthday" card (gold-on-black, KFS branded) similar in
 * style to the studio-designed reference card - starry background, gold frame,
 * script headline, bold name, insurance-umbrella logo motif, services list and
 * contact footer. Pure Java2D, no external assets.
 *
 * Compile:  javac BirthdayCardGenerator.java
 * Run:      java BirthdayCardGenerator "Soumya Das" "11-08-2019" card.png
 */
public class BirthdayCardGenerator {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1350;
    private static final Color GOLD = new Color(255, 205, 90);
    private static final Color GOLD_DARK = new Color(180, 130, 30);
    private static final Random RAND = new Random(42);

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "Customer Name";
        String dob = args.length > 1 ? args[1] : "";
        String outputPath = args.length > 2 ? args[2] : "birthday_card.png";

        BufferedImage img = generate(name, dob);
        ImageIO.write(img, "png", new java.io.File(outputPath));
        System.out.println("Saved: " + new java.io.File(outputPath).getAbsolutePath());
    }

    public static BufferedImage generate(String name, String dob) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        drawBackground(g);
        drawFrame(g);

        // Fixed absolute baselines for each section - avoids compounding-offset bugs
        // from chaining return values through fonts of very different heights.
        drawScriptHeadline(g, "Happy Birthday", 145);
        drawName(g, name.toUpperCase(), 270);
        drawDateLine(g, dob, 350);
        drawWishText(g, 415);

        drawLogo(g, 520);

        drawServicesHeading(g, 940);
        drawServiceLines(g, 985);

        drawFooter(g);

        g.dispose();
        return img;
    }

    // ---- Background: black with scattered stars + one shooting star streak ----
    private static void drawBackground(Graphics2D g) {
        GradientPaint bg = new GradientPaint(0, 0, new Color(10, 8, 15), WIDTH, HEIGHT, new Color(2, 2, 5));
        g.setPaint(bg);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (int i = 0; i < 260; i++) {
            int x = RAND.nextInt(WIDTH);
            int y = RAND.nextInt(HEIGHT);
            int size = 1 + RAND.nextInt(3);
            int alpha = 60 + RAND.nextInt(150);
            g.setColor(new Color(255, 235, 190, alpha));
            g.fillOval(x, y, size, size);
        }

        // Diagonal shooting star (bottom-left to mid, like the reference)
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GradientPaint streak = new GradientPaint(60, HEIGHT - 150, new Color(255, 210, 120, 0),
                340, HEIGHT - 420, new Color(255, 225, 160, 220));
        g.setPaint(streak);
        g.drawLine(60, HEIGHT - 150, 340, HEIGHT - 420);
        g.setColor(new Color(255, 240, 200));
        g.fillOval(330, HEIGHT - 430, 10, 10);
    }

    // ---- Ornate gold frame (simplified corner flourishes + double border line) ----
    private static void drawFrame(Graphics2D g) {
        g.setColor(GOLD_DARK);
        g.setStroke(new BasicStroke(3));
        g.drawRect(28, 28, WIDTH - 56, HEIGHT - 56);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1));
        g.drawRect(36, 36, WIDTH - 72, HEIGHT - 72);

        drawCornerFlourish(g, 40, 40, 1, 1);
        drawCornerFlourish(g, WIDTH - 40, 40, -1, 1);
        drawCornerFlourish(g, 40, HEIGHT - 40, 1, -1);
        drawCornerFlourish(g, WIDTH - 40, HEIGHT - 40, -1, -1);
    }

    /** A small quarter-circle + diamond flourish in one corner, mirrored via sx/sy = +-1. */
    private static void drawCornerFlourish(Graphics2D g, int x, int y, int sx, int sy) {
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2.5f));
        double startAngle = (sx > 0 && sy > 0) ? 180
                : (sx < 0 && sy > 0) ? 270
                : (sx > 0) ? 90
                : 0;
        for (int i = 0; i < 3; i++) {
            double r = 16 + i * 13;
            double left = x - r + (sx > 0 ? r : 0) - (sx < 0 ? -r : 0);
            // Anchor each quarter-arc's bounding box so it sweeps away from the corner.
            double bx = sx > 0 ? x - r : x - r;
            double by = sy > 0 ? y - r : y - r;
            Arc2D arc = new Arc2D.Double(bx, by, r * 2, r * 2, startAngle, 90, Arc2D.OPEN);
            g.draw(arc);
        }
        for (int i = 0; i < 2; i++) {
            int dx = x + sx * (48 + i * 34);
            int dy = y + sy * (48 + i * 34);
            drawDiamond(g, dx, dy, 5);
        }
    }

    private static void drawDiamond(Graphics2D g, int cx, int cy, int r) {
        Polygon p = new Polygon();
        p.addPoint(cx, cy - r);
        p.addPoint(cx + r, cy);
        p.addPoint(cx, cy + r);
        p.addPoint(cx - r, cy);
        g.setColor(Color.WHITE);
        g.fillPolygon(p);
        g.setColor(GOLD);
        g.drawPolygon(p);
    }

    private static int drawScriptHeadline(Graphics2D g, String text, int y) {
        Font font = italicFont("Serif", Font.BOLD | Font.ITALIC, 92);
        g.setFont(font);
        drawGoldGradientCentered(g, text, WIDTH / 2, y, font);
        return y;
    }

    private static int drawName(Graphics2D g, String name, int y) {
        Font font = fittedFont("SansSerif", Font.BOLD, 96, name, WIDTH - 100, g);
        // slight letter spacing for a "carved" look
        Map<TextAttribute, Object> attrs = new HashMap<>(font.getAttributes());
        attrs.put(TextAttribute.TRACKING, 0.03);
        Font tracked = font.deriveFont(attrs);
        g.setFont(tracked);
        drawGoldGradientCentered(g, name, WIDTH / 2, y, tracked);
        return y;
    }

    private static int drawDateLine(Graphics2D g, String dob, int y) {
        if (dob == null || dob.isBlank()) return y - 20;
        Font font = new Font("Serif", Font.BOLD, 44);
        g.setFont(font);
        drawGoldGradientCentered(g, dob, WIDTH / 2, y, font);

        FontMetrics fm = g.getFontMetrics(font);
        int halfWidth = fm.stringWidth(dob) / 2;
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2));
        g.drawLine(WIDTH / 2 - halfWidth - 90, y - 14, WIDTH / 2 - halfWidth - 20, y - 14);
        g.drawLine(WIDTH / 2 + halfWidth + 20, y - 14, WIDTH / 2 + halfWidth + 90, y - 14);
        drawDiamond(g, WIDTH / 2, y - 14, 6);
        return y;
    }

    private static int drawWishText(Graphics2D g, int y) {
        Font font = new Font("Serif", Font.ITALIC, 30);
        g.setFont(font);
        g.setColor(new Color(240, 235, 225));
        drawCentered(g, "Wishing you a great day to you and live a better life.", WIDTH / 2, y, font);
        drawCentered(g, "Take care of your family and be strong.", WIDTH / 2, y + 42, font);

        // small heart-ish flourish under the wish text
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2));
        g.drawLine(WIDTH / 2 - 130, y + 78, WIDTH / 2 - 30, y + 78);
        g.drawLine(WIDTH / 2 + 30, y + 78, WIDTH / 2 + 130, y + 78);
        drawDiamond(g, WIDTH / 2, y + 78, 6);
        return y + 78;
    }

    // ---- Logo: umbrella canopy + car/people/rupee/cross icons + KOLEY wordmark ----
    private static int drawLogo(Graphics2D g, int y) {
        int cx = WIDTH / 2;
        int canopyW = 300, canopyH = 90;

        // Canopy (blue, gold ribs)
        Arc2D canopy = new Arc2D.Double(cx - canopyW / 2.0, y, canopyW, canopyH * 2, 0, 180, Arc2D.CHORD);
        g.setColor(new Color(20, 60, 130));
        g.fill(canopy);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2));
        for (int i = -2; i <= 2; i++) {
            g.drawLine(cx, y + canopyH, cx + i * (canopyW / 5), y + canopyH - 6);
        }
        g.draw(canopy);

        // Pole
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(3));
        g.drawLine(cx, y + canopyH - 5, cx, y + canopyH + 95);

        // Icons row under the canopy: car | person | rupee | person | cross
        int iconY = y + canopyH + 40;
        drawCarIcon(g, cx - 130, iconY);
        drawPersonIcon(g, cx - 45, iconY);
        drawRupeeIcon(g, cx, iconY);
        drawPersonIcon(g, cx + 45, iconY);
        drawCrossIcon(g, cx + 125, iconY);

        // KOLEY wordmark
        Font koleyFont = new Font("SansSerif", Font.BOLD, 68);
        g.setFont(koleyFont);
        drawGoldGradientCentered(g, "KOLEY", cx, iconY + 100, koleyFont);

        Font subFont = new Font("SansSerif", Font.BOLD, 24);
        Map<TextAttribute, Object> attrs = new HashMap<>(subFont.getAttributes());
        attrs.put(TextAttribute.TRACKING, 0.15);
        Font tracked = subFont.deriveFont(attrs);
        g.setFont(tracked);
        drawGoldGradientCentered(g, "FINANCIAL SERVICES", cx, iconY + 138, tracked);

        return iconY + 138;
    }

    private static void drawCarIcon(Graphics2D g, int cx, int cy) {
        g.setColor(GOLD);
        g.fillRoundRect(cx - 22, cy - 8, 44, 16, 8, 8);
        g.fillRoundRect(cx - 12, cy - 16, 24, 12, 6, 6);
        g.setColor(new Color(20, 60, 130));
        g.fillOval(cx - 16, cy + 4, 10, 10);
        g.fillOval(cx + 6, cy + 4, 10, 10);
    }

    private static void drawPersonIcon(Graphics2D g, int cx, int cy) {
        g.setColor(GOLD);
        g.fillOval(cx - 8, cy - 20, 16, 16);
        g.fillRoundRect(cx - 12, cy - 2, 24, 26, 10, 10);
    }

    private static void drawRupeeIcon(Graphics2D g, int cx, int cy) {
        g.setColor(GOLD);
        Font f = new Font("SansSerif", Font.BOLD, 34);
        g.setFont(f);
        drawCentered(g, "\u20B9", cx, cy + 14, f);
    }

    private static void drawCrossIcon(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(20, 60, 130));
        g.fillRoundRect(cx - 6, cy - 18, 12, 36, 4, 4);
        g.fillRoundRect(cx - 18, cy - 6, 36, 12, 4, 4);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(cx - 18, cy - 18, 36, 36, 6, 6);
    }

    private static int drawServicesHeading(Graphics2D g, int y) {
        Font font = new Font("SansSerif", Font.BOLD, 30);
        g.setFont(font);
        String text = "OUR SERVICES:";
        drawGoldGradientCentered(g, text, WIDTH / 2, y, font);
        FontMetrics fm = g.getFontMetrics(font);
        int half = fm.stringWidth(text) / 2;
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2));
        g.drawLine(WIDTH / 2 - half - 70, y - 10, WIDTH / 2 - half - 15, y - 10);
        g.drawLine(WIDTH / 2 + half + 15, y - 10, WIDTH / 2 + half + 70, y - 10);
        return y;
    }

    private static int drawServiceLines(Graphics2D g, int y) {
        Font font = new Font("SansSerif", Font.PLAIN, 30);
        g.setFont(font);
        g.setColor(Color.WHITE);
        drawCentered(g, "Mutual Fund | Health Insurance | Motor Insurance", WIDTH / 2, y, font);
        drawCentered(g, "Term Insurance | Commercial Insurance | Loan", WIDTH / 2, y + 42, font);
        return y + 42;
    }

    private static void drawFooter(Graphics2D g) {
        int y = HEIGHT - 110;
        g.setColor(GOLD_DARK);
        g.setStroke(new BasicStroke(2));
        g.drawLine(70, y, WIDTH - 70, y);

        Font nameFont = new Font("SansSerif", Font.BOLD, 30);
        g.setFont(nameFont);
        drawGoldGradientCentered(g, "Avijit Koley | Anupam Koley | Avirup Koley", WIDTH / 2, y + 45, nameFont);

        Font phoneFont = new Font("SansSerif", Font.PLAIN, 28);
        g.setFont(phoneFont);
        g.setColor(Color.WHITE);
        drawCentered(g, "\u260E 9434643120 | 6295177928 | 9476210772", WIDTH / 2, y + 90, phoneFont);
    }

    // ---- text helpers ----
    private static void drawCentered(Graphics2D g, String text, int centerX, int y, Font font) {
        FontMetrics fm = g.getFontMetrics(font);
        g.drawString(text, centerX - fm.stringWidth(text) / 2, y);
    }

    /** Draws text with a simple vertical gold gradient + a soft dark shadow for a metallic look. */
    private static void drawGoldGradientCentered(Graphics2D g, String text, int centerX, int y, Font font) {
        FontMetrics fm = g.getFontMetrics(font);
        int x = centerX - fm.stringWidth(text) / 2;

        g.setColor(new Color(0, 0, 0, 140));
        g.drawString(text, x + 3, y + 3);

        GradientPaint gradient = new GradientPaint(
                x, y - fm.getAscent(), new Color(255, 240, 200),
                x, y + fm.getDescent(), new Color(170, 120, 30));
        g.setPaint(gradient);
        g.drawString(text, x, y);
        g.setPaint(null);
    }

    private static Font italicFont(String family, int style, int size) {
        return new Font(family, style, size);
    }

    private static Font fittedFont(String family, int style, int startSize, String text, int maxWidth, Graphics2D g) {
        int size = startSize;
        Font font = new Font(family, style, size);
        while (size > 40) {
            font = new Font(family, style, size);
            FontMetrics fm = g.getFontMetrics(font);
            if (fm.stringWidth(text) <= maxWidth) break;
            size -= 4;
        }
        return font; //test
    }
}
