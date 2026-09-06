package com.premiumreminder.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class BirthdayCardService {

    private static final String TEMPLATE_PATH = "templates/birthday-template.png";
    private static final String FONT_NAME = "Serif";

    private static final int NAME_FONT_SIZE = 100;
    private static final int NAME_Y = 340;

    private static final int DATE_FONT_SIZE = 48;
    private static final int DATE_Y = 445;

    public byte[] generateCard(String customerName, String dateOfBirth) throws IOException {
        BufferedImage template = loadTemplate();

        BufferedImage output = new BufferedImage(
                template.getWidth(), template.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(template, 0, 0, null);

        drawGoldCenteredText(g, customerName, template.getWidth(), NAME_Y, NAME_FONT_SIZE);

        if (dateOfBirth != null && !dateOfBirth.isBlank()) {
            drawGoldCenteredText(g, dateOfBirth, template.getWidth(), DATE_Y, DATE_FONT_SIZE);
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(output, "png", baos);
        return baos.toByteArray();
    }

    private void drawGoldCenteredText(Graphics2D g, String text, int imageWidth, int y, int fontSize) {
        g.setFont(new Font(FONT_NAME, Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int drawX = (imageWidth - fm.stringWidth(text)) / 2;

        g.setColor(new Color(0, 0, 0, 140));
        g.drawString(text, drawX + 3, y + 3);

        GradientPaint gradient = new GradientPaint(
                drawX, y - fm.getAscent(), new Color(255, 240, 200),
                drawX, y + fm.getDescent(), new Color(180, 130, 30));
        g.setPaint(gradient);
        g.drawString(text, drawX, y);
        g.setPaint(null);
    }

    private BufferedImage loadTemplate() throws IOException {
        try (InputStream is = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            return ImageIO.read(is);
        }
    }
}