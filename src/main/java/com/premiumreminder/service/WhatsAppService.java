package com.premiumreminder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WhatsAppService {

    private final RestClient restClient = RestClient.create("https://graph.facebook.com");

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    // ... existing template-name/@Value fields unchanged ...

    @Value("${app.whatsapp.template-name:premium_reminder}")
    private String templateName;
    @Value("${app.whatsapp.template-language:en}")
    private String templateLanguage;

    @Value("${app.whatsapp.template-name-hi:premium_reminder_hi}")
    private String templateNameHi;
    @Value("${app.whatsapp.template-language-hi:hi}")
    private String templateLanguageHi;

    @Value("${app.whatsapp.template-name-bn:premium_reminder_bn}")
    private String templateNameBn;
    @Value("${app.whatsapp.template-language-bn:bn}")
    private String templateLanguageBn;

    @Value("${app.whatsapp.birthday-template-name:birthday_greeting_en}")
    private String birthdayTemplateName;
    @Value("${app.whatsapp.birthday-template-language:en_US}")
    private String birthdayTemplateLanguage;

    @Value("${app.whatsapp.birthday-template-name-hi:birthday_greeting_hi}")
    private String birthdayTemplateNameHi;
    @Value("${app.whatsapp.birthday-template-language-hi:hi_IN}")
    private String birthdayTemplateLanguageHi;

    @Value("${app.whatsapp.birthday-template-name-bn:birthday_greeting_bn}")
    private String birthdayTemplateNameBn;
    @Value("${app.whatsapp.birthday-template-language-bn:bn_IN}")
    private String birthdayTemplateLanguageBn;

    // --- New: image-header birthday template ---
    @Value("${app.whatsapp.birthday-image-template-name:birthday_greeting_image_en}")
    private String birthdayImageTemplateName;
    @Value("${app.whatsapp.birthday-image-template-language:en}")
    private String birthdayImageTemplateLanguage;

    @Value("${app.whatsapp.admin-due-tomorrow-template-name:premium_due_tomorrow_admin}")
    private String adminDueTomorrowTemplateName;
    @Value("${app.whatsapp.admin-due-tomorrow-template-language:en}")
    private String adminDueTomorrowTemplateLanguage;

    public void sendTemplate(String mobileWithCountryCode, List<String> bodyParams) {
        sendTemplate(mobileWithCountryCode, bodyParams, null);
    }

    public void sendTemplate(String mobileWithCountryCode, List<String> bodyParams, String preferredLanguage) {
        String name;
        String language;
        if ("HINDI".equalsIgnoreCase(preferredLanguage)) {
            name = templateNameHi;
            language = templateLanguageHi;
        } else if ("BENGALI".equalsIgnoreCase(preferredLanguage)) {
            name = templateNameBn;
            language = templateLanguageBn;
        } else {
            name = templateName;
            language = templateLanguage;
        }

        List<Map<String, String>> parameters = bodyParams.stream()
                .map(p -> Map.of("type", "text", "text", p))
                .toList();

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", mobileWithCountryCode,
                "type", "template",
                "template", Map.of(
                        "name", name,
                        "language", Map.of("code", language),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", parameters
                        ))
                )
        );

        restClient.post()
                .uri("/v20.0/{phoneNumberId}/messages", phoneNumberId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("WhatsApp template message ({}) sent to {}", language, mobileWithCountryCode);
    }

    public void sendBirthdayTemplate(String mobileWithCountryCode, List<String> bodyParams) {
        sendBirthdayTemplate(mobileWithCountryCode, bodyParams, null);
    }

    public void sendBirthdayTemplate(String mobileWithCountryCode, List<String> bodyParams, String preferredLanguage) {
        String name;
        String language;
        if ("HINDI".equalsIgnoreCase(preferredLanguage)) {
            name = birthdayTemplateNameHi;
            language = birthdayTemplateLanguageHi;
        } else if ("BENGALI".equalsIgnoreCase(preferredLanguage)) {
            name = birthdayTemplateNameBn;
            language = birthdayTemplateLanguageBn;
        } else {
            name = birthdayTemplateName;
            language = birthdayTemplateLanguage;
        }

        List<Map<String, String>> parameters = bodyParams.stream()
                .map(p -> Map.of("type", "text", "text", p))
                .toList();

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", mobileWithCountryCode,
                "type", "template",
                "template", Map.of(
                        "name", name,
                        "language", Map.of("code", language),
                        "components", List.of(
                                Map.of(
                                        "type", "header",
                                        "parameters", List.of(Map.of("type", "text", "text", bodyParams.get(0)))
                                ),
                                Map.of(
                                        "type", "body",
                                        "parameters", parameters
                                )
                        )
                )
        );

        restClient.post()
                .uri("/v20.0/{phoneNumberId}/messages", phoneNumberId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("WhatsApp birthday template ({}) sent to {}", language, mobileWithCountryCode);
    }

    /**
     * Uploads raw image bytes to WhatsApp's Media API and returns the resulting media ID,
     * which can then be referenced in a template's IMAGE header component. Media IDs are
     * only valid for a limited time (per Meta's docs) - upload right before sending, don't
     * cache long-term.
     */
    public String uploadMedia(byte[] imageBytes, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("messaging_product", "whatsapp");
        form.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        Map<String, Object> response = restClient.post()
                .uri("/v20.0/{phoneNumberId}/media", phoneNumberId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Map.class);

        String mediaId = (String) response.get("id");
        log.info("Uploaded WhatsApp media, id={}", mediaId);
        return mediaId;
    }

    /**
     * Sends the birthday template whose header is an IMAGE component, using a media ID
     * obtained from uploadMedia(). bodyParams fill the template body's {{1}}, {{2}}, ...
     * placeholders; the image itself fills the header - no separate header text parameter
     * needed for an image-header template.
     */
    public void sendBirthdayTemplateWithImage(String mobileWithCountryCode, String mediaId,
                                              List<String> bodyParams, String preferredLanguage) {
        // If you add per-language image templates later, branch here the same way
        // sendBirthdayTemplate() does. For now this uses a single image template.
        String name = birthdayImageTemplateName;
        String language = birthdayImageTemplateLanguage;

        List<Map<String, String>> parameters = bodyParams.stream()
                .map(p -> Map.of("type", "text", "text", p))
                .toList();

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", mobileWithCountryCode,
                "type", "template",
                "template", Map.of(
                        "name", name,
                        "language", Map.of("code", language),
                        "components", List.of(
                                Map.of(
                                        "type", "header",
                                        "parameters", List.of(Map.of(
                                                "type", "image",
                                                "image", Map.of("id", mediaId)
                                        ))
                                ),
                                Map.of(
                                        "type", "body",
                                        "parameters", parameters
                                )
                        )
                )
        );

        restClient.post()
                .uri("/v20.0/{phoneNumberId}/messages", phoneNumberId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("WhatsApp birthday image template ({}) sent to {}", language, mobileWithCountryCode);
    }

    /**
     * "Premium due tomorrow" alert to an admin, with a one-tap mark-paid link as the
     * final body parameter. Admin-facing, so it always uses the single English admin
     * template regardless of any customer's messageLanguage - there's no per-language
     * branching here the way sendTemplate()/sendBirthdayTemplate() do for customers.
     */
    public void sendPremiumDueTomorrowAdminTemplate(String mobileWithCountryCode,
                                                    String customerName,
                                                    String policyNumber,
                                                    String insurerName,
                                                    String premiumAmount,
                                                    String dueDate,
                                                    String markPaidLink) {
        List<Map<String, String>> parameters = List.of(
                Map.of("type", "text", "text", customerName),
                Map.of("type", "text", "text", policyNumber),
                Map.of("type", "text", "text", insurerName),
                Map.of("type", "text", "text", premiumAmount),
                Map.of("type", "text", "text", dueDate),
                Map.of("type", "text", "text", markPaidLink)
        );

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", mobileWithCountryCode,
                "type", "template",
                "template", Map.of(
                        "name", adminDueTomorrowTemplateName,
                        "language", Map.of("code", adminDueTomorrowTemplateLanguage),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", parameters
                        ))
                )
        );

        restClient.post()
                .uri("/v20.0/{phoneNumberId}/messages", phoneNumberId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("WhatsApp admin due-tomorrow template sent to {}", mobileWithCountryCode);
    }
}