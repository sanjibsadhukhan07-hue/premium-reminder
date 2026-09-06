package com.premiumreminder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends WhatsApp messages via Meta's WhatsApp Cloud API
 * (https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-message-templates).
 *
 * Business-initiated messages (like a proactive premium reminder or birthday wish) MUST
 * use a pre-approved message Template - free-form text only works as a reply within
 * 24 hours of the customer messaging you first.
 *
 * Template selection is driven by the customer/relative's messageLanguage field
 * ("ENGLISH", "HINDI", "BENGALI") - each language needs its own pre-approved template in
 * the WhatsApp Business Manager, since Meta templates aren't dynamically translated.
 */
@Service
@Slf4j
public class WhatsAppService {

    private final RestClient restClient = RestClient.create("https://graph.facebook.com");

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    // --- Premium reminder templates, one per language ---
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

    // --- Birthday templates, one per language ---
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

    /**
     * Sends the approved premium-reminder template in English (default) - kept for any
     * caller that doesn't pass a language.
     */
    public void sendTemplate(String mobileWithCountryCode, List<String> bodyParams) {
        sendTemplate(mobileWithCountryCode, bodyParams, null);
    }

    /**
     * Sends the approved premium-reminder template in the customer's preferred language.
     * preferredLanguage is the Customer's messageLanguage value ("ENGLISH", "HINDI",
     * "BENGALI") - anything else (or null) falls back to English. bodyParams must match,
     * in order, the {{1}}, {{2}}, ... placeholders defined in the selected template's body.
     */
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

    /**
     * Sends the approved WhatsApp birthday-wish template in English (default) - kept for
     * any caller that doesn't pass a language.
     */
    public void sendBirthdayTemplate(String mobileWithCountryCode, List<String> bodyParams) {
        sendBirthdayTemplate(mobileWithCountryCode, bodyParams, null);
    }

    /**
     * Sends the approved WhatsApp birthday-wish template in the customer/relative's
     * preferred language. preferredLanguage is the messageLanguage value ("ENGLISH",
     * "HINDI", "BENGALI") - anything else (or null) falls back to English.
     */
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
}