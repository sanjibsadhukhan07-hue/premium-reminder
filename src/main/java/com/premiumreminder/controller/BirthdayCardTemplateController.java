package com.premiumreminder.controller;

import com.premiumreminder.service.BirthdayCardService;
import com.premiumreminder.service.BirthdayCardTemplateService;
import com.premiumreminder.service.CardTextPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/birthday-template")
@RequiredArgsConstructor
public class BirthdayCardTemplateController {

    private final BirthdayCardTemplateService templateService;
    private final BirthdayCardService birthdayCardService;

    @GetMapping
    public String settingsPage(Model model) {
        model.addAttribute("template", templateService.findCurrent().orElse(null));
        model.addAttribute("position", templateService.getActivePosition());
        return "admin/birthday-template";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            templateService.replace(file);
            redirectAttributes.addFlashAttribute("templateSaved", "Birthday card template updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("templateError", "Upload failed: " + e.getMessage());
        }
        return "redirect:/admin/birthday-template";
    }

    @PostMapping("/positions")
    public String savePositions(@RequestParam int nameY,
                                @RequestParam int dateY,
                                @RequestParam int nameFontSizeMax,
                                @RequestParam int nameFontSizeMin,
                                @RequestParam int dateFontSize,
                                @RequestParam int sideMargin,
                                @RequestParam String textColorHex,
                                @RequestParam String shadowColorHex,
                                RedirectAttributes redirectAttributes) {
        try {
            CardTextPosition position = new CardTextPosition(
                    nameY, dateY, nameFontSizeMax, nameFontSizeMin, dateFontSize, sideMargin,
                    textColorHex, shadowColorHex);
            templateService.updatePosition(position);
            redirectAttributes.addFlashAttribute("templateSaved", "Text position settings saved.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("templateError", "Could not save positions: " + e.getMessage());
        }
        return "redirect:/admin/birthday-template";
    }

    @GetMapping("/preview")
    public ResponseEntity<byte[]> preview() {
        var current = templateService.findCurrent().orElse(null);
        if (current == null || current.getImageData() == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = current.getContentType() != null
                ? MediaType.parseMediaType(current.getContentType())
                : MediaType.IMAGE_PNG;
        return ResponseEntity.ok().contentType(mediaType).body(current.getImageData());
    }

    @GetMapping(value = "/sample", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> sample(@RequestParam(defaultValue = "Avijit Koley") String name,
                                         @RequestParam(defaultValue = "15-08-1985") String dob,
                                         @RequestParam(required = false) Integer nameY,
                                         @RequestParam(required = false) Integer dateY,
                                         @RequestParam(required = false) Integer nameFontSizeMax,
                                         @RequestParam(required = false) Integer nameFontSizeMin,
                                         @RequestParam(required = false) Integer dateFontSize,
                                         @RequestParam(required = false) Integer sideMargin,
                                         @RequestParam(required = false) String textColorHex,
                                         @RequestParam(required = false) String shadowColorHex) {
        try {
            CardTextPosition base = templateService.getActivePosition();
            CardTextPosition position = new CardTextPosition(
                    nameY != null ? nameY : base.nameY(),
                    dateY != null ? dateY : base.dateY(),
                    nameFontSizeMax != null ? nameFontSizeMax : base.nameFontSizeMax(),
                    nameFontSizeMin != null ? nameFontSizeMin : base.nameFontSizeMin(),
                    dateFontSize != null ? dateFontSize : base.dateFontSize(),
                    sideMargin != null ? sideMargin : base.sideMargin(),
                    (textColorHex != null && !textColorHex.isBlank()) ? textColorHex : base.textColorHex(),
                    (shadowColorHex != null && !shadowColorHex.isBlank()) ? shadowColorHex : base.shadowColorHex()
            );
            byte[] bytes = birthdayCardService.generateCard(name, dob, position);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}