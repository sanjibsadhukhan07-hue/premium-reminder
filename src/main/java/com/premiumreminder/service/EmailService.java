package com.premiumreminder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends email via the Resend HTTP API instead of raw SMTP.
 * Railway blocks outbound SMTP (ports 25/465/587) on Free/Trial/Hobby plans,
 * so we go over HTTPS instead — this works on any plan/host.
 */
@Service
@Slf4j
public class EmailService {

    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.resend-api-key}")
    private String resendApiKey;

    /**
     * Sends a plain text email via Resend. Throws on failure so the caller can log the outcome.
     */
    public void send(String toEmail, String subject, String body) {
        try {
            Map<String, Object> payload = Map.of(
                    "from", fromAddress,
                    "to", List.of(toEmail),
                    "subject", subject,
                    "text", body
            );

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent to {}", toEmail);
            } else {
                throw new RuntimeException("Resend API returned " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            // Re-throw as unchecked so NotificationService's existing try/catch + logAttempt still works unchanged
            throw new RuntimeException("Failed to send email via Resend: " + e.getMessage(), e);
        }
    }
}