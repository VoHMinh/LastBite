package com.LastBite.common.service;

import com.LastBite.common.email.EmailDeliveryLog;
import com.LastBite.common.email.EmailDeliveryLogRepository;
import com.LastBite.common.email.EmailDeliveryStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendWebhookService {

    private final EmailDeliveryLogRepository deliveryLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("type").asText("");
            String providerMessageId = providerMessageId(root.path("data"));
            if (providerMessageId.isBlank()) {
                log.warn("Resend webhook without provider message id: {}", eventType);
                return;
            }

            EmailDeliveryStatus status = mapStatus(eventType);
            if (status == null) {
                log.debug("Ignoring unsupported Resend webhook event type {}", eventType);
                return;
            }

            deliveryLogRepository.findFirstByProviderMessageIdOrderByCreatedAtDesc(providerMessageId)
                    .ifPresentOrElse(
                            logEntry -> updateLog(logEntry, status, payload),
                            () -> log.warn("Resend webhook {} references unknown email id {}", eventType, providerMessageId));
        } catch (Exception e) {
            log.warn("Unable to process Resend webhook: {}", e.getMessage());
        }
    }

    private void updateLog(EmailDeliveryLog logEntry, EmailDeliveryStatus status, String payload) {
        logEntry.setStatus(status);
        logEntry.setEventPayload(payload);
        if (status == EmailDeliveryStatus.DELIVERED) {
            logEntry.setDeliveredAt(Instant.now());
        }
        if (status == EmailDeliveryStatus.FAILED
                || status == EmailDeliveryStatus.BOUNCED
                || status == EmailDeliveryStatus.COMPLAINED
                || status == EmailDeliveryStatus.SUPPRESSED) {
            logEntry.setLastError(status.name());
        }
        deliveryLogRepository.save(logEntry);
    }

    private EmailDeliveryStatus mapStatus(String eventType) {
        return switch (eventType) {
            case "email.sent" -> EmailDeliveryStatus.SENT;
            case "email.delivered" -> EmailDeliveryStatus.DELIVERED;
            case "email.delivery_delayed" -> EmailDeliveryStatus.DELIVERY_DELAYED;
            case "email.failed" -> EmailDeliveryStatus.FAILED;
            case "email.bounced" -> EmailDeliveryStatus.BOUNCED;
            case "email.complained" -> EmailDeliveryStatus.COMPLAINED;
            case "email.suppressed" -> EmailDeliveryStatus.SUPPRESSED;
            default -> null;
        };
    }

    private String providerMessageId(JsonNode data) {
        String emailId = data.path("email_id").asText("");
        if (!emailId.isBlank()) {
            return emailId;
        }
        String id = data.path("id").asText("");
        if (!id.isBlank()) {
            return id;
        }
        return data.path("email").path("id").asText("");
    }
}
