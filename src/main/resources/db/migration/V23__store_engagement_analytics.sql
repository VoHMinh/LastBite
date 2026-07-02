CREATE TABLE IF NOT EXISTS store_engagement_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    bag_id UUID REFERENCES surprise_bags(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(30) NOT NULL,
    source VARCHAR(80),
    session_id VARCHAR(120),
    referrer VARCHAR(500),
    user_agent VARCHAR(500),
    ip_hash VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_store_engagement_event_type
        CHECK (event_type IN ('STORE_VIEW', 'STORE_CARD_CLICK', 'BAG_VIEW', 'BAG_CARD_CLICK'))
);

CREATE INDEX IF NOT EXISTS idx_store_engagement_store_time
    ON store_engagement_events(store_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_store_engagement_bag_time
    ON store_engagement_events(bag_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_store_engagement_type_time
    ON store_engagement_events(event_type, occurred_at);

CREATE INDEX IF NOT EXISTS idx_store_engagement_source
    ON store_engagement_events(source);
