package com.LastBite.common.email;

import com.LastBite.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "email_delivery_logs", indexes = {
        @Index(name = "idx_email_delivery_provider_message", columnList = "provider_message_id"),
        @Index(name = "idx_email_delivery_recipient_created", columnList = "recipient_email,created_at"),
        @Index(name = "idx_email_delivery_status_created", columnList = "status,created_at")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDeliveryLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 60)
    private EmailMessageType messageType;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 256)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EmailDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "event_payload", columnDefinition = "TEXT")
    private String eventPayload;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
