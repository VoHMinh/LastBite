package com.LastBite.common.service;

import com.LastBite.common.email.EmailDeliveryLog;
import com.LastBite.common.email.EmailDeliveryLogRepository;
import com.LastBite.common.email.EmailDeliveryStatus;
import com.LastBite.common.email.EmailMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResendWebhookServiceTest {

    private final EmailDeliveryLogRepository repository = mock(EmailDeliveryLogRepository.class);
    private final ResendWebhookService service = new ResendWebhookService(repository, new ObjectMapper());

    @Test
    void deliveredEventUpdatesMatchingDeliveryLog() {
        EmailDeliveryLog log = EmailDeliveryLog.builder()
                .messageType(EmailMessageType.OTP)
                .provider("RESEND")
                .providerMessageId("email-123")
                .recipientEmail("customer@example.com")
                .subject("Ma xac minh LastBite")
                .idempotencyKey("idem-123")
                .status(EmailDeliveryStatus.SENT)
                .attempts(1)
                .build();
        when(repository.findFirstByProviderMessageIdOrderByCreatedAtDesc("email-123"))
                .thenReturn(Optional.of(log));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.handle("""
                {
                  "type": "email.delivered",
                  "data": {
                    "email_id": "email-123"
                  }
                }
                """);

        assertEquals(EmailDeliveryStatus.DELIVERED, log.getStatus());
        assertNotNull(log.getDeliveredAt());
        verify(repository).save(log);
    }
}
