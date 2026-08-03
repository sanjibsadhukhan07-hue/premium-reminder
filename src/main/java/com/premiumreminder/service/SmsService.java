package com.premiumreminder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends SMS via Fast2SMS's "Quick SMS" route (https://docs.fast2sms.com/reference/quick-sms).
 * Chosen for simplicity: no DLT registration, no Sender ID approval, no business documents
 * needed - works for individuals/small projects. Costs more per SMS than a DLT route, so
 * reconsider for high volume later.
 *
 * Only one credential is required: the API key from the Fast2SMS dashboard (Dev API section).
 */
@Service
@Slf4j
public class SmsService {

    private final RestClient restClient = RestClient.create("https://www.fast2sms.com");

    @Value("${app.sms.fast2sms.api-key}")
    private String apiKey;

    /**
     * Sends a plain-text SMS to an Indian mobile number (10 digits, no country code / no "+").
     */
    public void send(String mobile, String message) {
        Map<String, Object> payload = Map.of(
                "route", "q",
                "message", message,
                "numbers", mobile
        );

        restClient.post()
                .uri("/dev/bulkV2")
                .header("authorization", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("SMS request sent to {}", mobile);
    }
}