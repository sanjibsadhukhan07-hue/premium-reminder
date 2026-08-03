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
 * Business-initiated messages (like a proactive premium reminder) MUST use a pre-approved
 * message Template - free-form text only works as a reply within 24 hours of the customer
 * messaging you first. Create and get a template approved in Meta Business Manager, then set
 * its name via app.whatsapp.template-name.
 */
@Service
@Slf4j
public class WhatsAppService {

    private final RestClient restClient = RestClient.create("https://graph.facebook.com");

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.whatsapp.template-name:premium_reminder}")
    private String templateName;

    @Value("${app.whatsapp.template-language:en_US}")
    private String templateLanguage;

    /**
     * Sends an approved WhatsApp template message. bodyParams must match, in order, the
     * {{1}}, {{2}}, ... placeholders defined in your approved template's body text.
     * mobileWithCountryCode must include the country code with no "+" and no leading zero,
     * e.g. "919812345678".
     */
    public void sendTemplate(String mobileWithCountryCode, List<String> bodyParams) {
        List<Map<String, String>> parameters = bodyParams.stream()
                .map(p -> Map.of("type", "text", "text", p))
                .toList();

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", mobileWithCountryCode,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", templateLanguage),
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

        log.info("WhatsApp template message sent to {}", mobileWithCountryCode);
    }
}