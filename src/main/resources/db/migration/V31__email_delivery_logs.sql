CREATE TABLE IF NOT EXISTS email_delivery_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type VARCHAR(60) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_message_id VARCHAR(120),
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    event_payload TEXT,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_email_delivery_status CHECK (
        status IN ('PENDING', 'SENT', 'DELIVERED', 'DELIVERY_DELAYED', 'FAILED', 'BOUNCED', 'COMPLAINED', 'SUPPRESSED')
    ),
    CONSTRAINT chk_email_delivery_message_type CHECK (
        message_type IN ('OTP', 'VERIFICATION_LINK', 'ACCOUNT_DELETION_VERIFICATION', 'ACCOUNT_DELETION_SCHEDULED', 'TEST')
    )
);

CREATE INDEX IF NOT EXISTS idx_email_delivery_provider_message
    ON email_delivery_logs(provider_message_id);

CREATE INDEX IF NOT EXISTS idx_email_delivery_recipient_created
    ON email_delivery_logs(recipient_email, created_at);

CREATE INDEX IF NOT EXISTS idx_email_delivery_status_created
    ON email_delivery_logs(status, created_at);
