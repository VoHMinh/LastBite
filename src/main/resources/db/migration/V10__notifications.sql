CREATE TABLE notification_devices (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    device_token VARCHAR(500) NOT NULL,
    device_type VARCHAR(20) NOT NULL,
    app_version VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notification_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_devices_user_token UNIQUE (user_id, device_token),
    CONSTRAINT chk_notification_devices_type CHECK (device_type IN ('IOS', 'ANDROID', 'WEB'))
);

CREATE INDEX idx_notification_devices_user_active ON notification_devices(user_id, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_notification_devices_token_active ON notification_devices(device_token, is_active) WHERE is_active = TRUE;

CREATE TABLE notifications (
    id UUID PRIMARY KEY NOT NULL,
    recipient_id UUID NOT NULL,
    type VARCHAR(60) NOT NULL,
    category VARCHAR(30) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    image_url VARCHAR(500),
    deep_link VARCHAR(500),
    reference_type VARCHAR(30),
    reference_id UUID,
    payload JSONB,
    dedupe_key VARCHAR(180) UNIQUE,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_category CHECK (category IN ('ORDER', 'PICKUP', 'PROMOTION', 'STORE', 'MERCHANT', 'SYSTEM')),
    CONSTRAINT chk_notifications_reference_type CHECK (reference_type IS NULL OR reference_type IN ('ORDER', 'BAG', 'STORE', 'CAMPAIGN', 'USER', 'SYSTEM'))
);

CREATE INDEX idx_notifications_recipient_created_at ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_notifications_recipient_unread ON notifications(recipient_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_reference ON notifications(reference_type, reference_id);

CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY NOT NULL,
    notification_id UUID NOT NULL,
    device_id UUID,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(255),
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notification_deliveries_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_deliveries_device FOREIGN KEY (device_id) REFERENCES notification_devices(id) ON DELETE SET NULL,
    CONSTRAINT chk_notification_deliveries_channel CHECK (channel IN ('FCM', 'EMAIL')),
    CONSTRAINT chk_notification_deliveries_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_notification_deliveries_notification ON notification_deliveries(notification_id);
CREATE INDEX idx_notification_deliveries_status ON notification_deliveries(status);

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    category VARCHAR(30) NOT NULL,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_preferences_user_category UNIQUE (user_id, category),
    CONSTRAINT chk_notification_preferences_category CHECK (category IN ('ORDER', 'PICKUP', 'PROMOTION', 'STORE', 'MERCHANT', 'SYSTEM'))
);

CREATE INDEX idx_notification_preferences_user ON notification_preferences(user_id);
