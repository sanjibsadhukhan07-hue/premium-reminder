package com.premiumreminder.service;

import com.premiumreminder.model.BirthdayCardTemplate;
import com.premiumreminder.repository.BirthdayCardTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BirthdayCardTemplateService {

    private static final String FALLBACK_PATH = "templates/birthday-template.png";
    private static final List<String> ALLOWED_TYPES = List.of("image/png", "image/jpeg");

    private final BirthdayCardTemplateRepository repository;

    public Optional<BirthdayCardTemplate> findCurrent() {
        return repository.findById(1L);
    }

    public byte[] getActiveImageBytes() throws IOException {
        return findCurrent()
                .map(BirthdayCardTemplate::getImageData)
                .orElseGet(this::loadFallbackBytes);
    }

    public CardTextPosition getActivePosition() {
        CardTextPosition defaults = CardTextPosition.defaults();
        return findCurrent()
                .map(t -> new CardTextPosition(
                        t.getNameY() != null ? t.getNameY() : defaults.nameY(),
                        t.getDateY() != null ? t.getDateY() : defaults.dateY(),
                        t.getNameFontSizeMax() != null ? t.getNameFontSizeMax() : defaults.nameFontSizeMax(),
                        t.getNameFontSizeMin() != null ? t.getNameFontSizeMin() : defaults.nameFontSizeMin(),
                        t.getDateFontSize() != null ? t.getDateFontSize() : defaults.dateFontSize(),
                        t.getSideMargin() != null ? t.getSideMargin() : defaults.sideMargin(),
                        t.getTextColorHex() != null ? t.getTextColorHex() : defaults.textColorHex(),
                        t.getShadowColorHex() != null ? t.getShadowColorHex() : defaults.shadowColorHex()
                ))
                .orElse(defaults);
    }

    @Transactional
    public BirthdayCardTemplate replace(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No image file was provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only PNG or JPEG images are allowed");
        }

        byte[] bytes = file.getBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IllegalArgumentException("File could not be read as an image");
        }

        BirthdayCardTemplate template = repository.findById(1L).orElseGet(BirthdayCardTemplate::new);
        template.setId(1L);
        template.setImageData(bytes);
        template.setContentType(contentType);
        template.setFileName(file.getOriginalFilename());
        template.setWidth(image.getWidth());
        template.setHeight(image.getHeight());
        template.setUpdatedAt(LocalDateTime.now());

        return repository.save(template);
    }

    @Transactional
    public BirthdayCardTemplate updatePosition(CardTextPosition position) {
        BirthdayCardTemplate template = repository.findById(1L).orElseGet(BirthdayCardTemplate::new);
        template.setId(1L);
        template.setNameY(position.nameY());
        template.setDateY(position.dateY());
        template.setNameFontSizeMax(position.nameFontSizeMax());
        template.setNameFontSizeMin(position.nameFontSizeMin());
        template.setDateFontSize(position.dateFontSize());
        template.setSideMargin(position.sideMargin());
        template.setTextColorHex(position.textColorHex());
        template.setShadowColorHex(position.shadowColorHex());
        template.setUpdatedAt(LocalDateTime.now());
        return repository.save(template);
    }

    private byte[] loadFallbackBytes() {
        try (InputStream is = new ClassPathResource(FALLBACK_PATH).getInputStream()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No birthday card template uploaded and fallback resource is missing", e);
        }
    }
}