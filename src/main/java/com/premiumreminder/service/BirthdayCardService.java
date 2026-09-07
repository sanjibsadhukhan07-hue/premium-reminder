package com.premiumreminder.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class BirthdayCardService {

    private static final String FONT_NAME = "Serif";

    private final BirthdayCardTemplateService templateService;

    public byte[] generateCard(String customerName, String dateOfBirth) throws IOException {
        return generateCard(customerName, dateOfBirth, templateService.getActivePosition());
    }

    public byte[] generateCard(String customerName, String dateOfBirth, CardTextPosition position) throws IOException {
        BufferedImage template = loadTemplate();

        BufferedImage output = new BufferedImage(
                template.getWidth(), template.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(template, 0, 0, null);

        int maxTextWidth = template.getWidth() - (position.sideMargin() * 2);

        Font nameFont = fittedFont(g, customerName, maxTextWidth,
                position.nameFontSizeMax(), position.nameFontSizeMin());
        drawCenteredText(g, customerName, template.getWidth(), position.nameY(), nameFont, position);

        if (dateOfBirth != null && !dateOfBirth.isBlank()) {
            Font dateFont = new Font(FONT_NAME, Font.BOLD, position.dateFontSize());
            drawCenteredText(g, dateOfBirth, template.getWidth(), position.dateY(), dateFont, position);
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(output, "png", baos);
        return baos.toByteArray();
    }

    private Font fittedFont(Graphics2D g, String text, int maxWidth, int maxSize, int minSize) {
        int size = maxSize;
        Font font = new Font(FONT_NAME, Font.BOLD, size);
        while (size > minSize) {
            font = new Font(FONT_NAME, Font.BOLD, size);
            FontMetrics fm = g.getFontMetrics(font);
            if (fm.stringWidth(text) <= maxWidth) break;
            size -= 2;
        }
        return font;
    }

    private void drawCenteredText(Graphics2D g, String text, int imageWidth, int y, Font font, CardTextPosition position) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int drawX = (imageWidth - fm.stringWidth(text)) / 2;

        g.setColor(position.shadowColor());
        g.drawString(text, drawX + 2, y + 2);

        g.setColor(position.textColor());
        g.drawString(text, drawX, y);
    }

    private BufferedImage loadTemplate() throws IOException {
        byte[] bytes = templateService.getActiveImageBytes();
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}