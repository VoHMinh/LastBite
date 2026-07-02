ALTER TABLE store_reliability_stats
    ADD COLUMN IF NOT EXISTS merchant_cancelled_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS store_fault_refund_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_warning_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_recalculated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_store_reliability_suspended_until
    ON store_reliability_stats(suspended_until);
CREATE INDEX IF NOT EXISTS idx_store_reliability_under_review
    ON store_reliability_stats(is_under_review);

ALTER TABLE stock_audit_logs
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(30) NOT NULL DEFAULT 'SYSTEM';

UPDATE stock_audit_logs
SET actor_type = CASE
    WHEN action IN ('STOCK_ADD', 'STOCK_REDUCE', 'STOCK_SET') AND actor_id IS NOT NULL THEN 'MERCHANT'
    WHEN action = 'SELL' THEN 'PAYMENT_PROVIDER'
    WHEN action IN ('RESERVE', 'RESERVE_CANCEL') AND actor_id IS NOT NULL THEN 'CUSTOMER'
    WHEN action = 'EXPIRE_UNSOLD' THEN 'SYSTEM'
    ELSE actor_type
END;

ALTER TABLE stock_audit_logs
    DROP CONSTRAINT IF EXISTS chk_stock_audit_logs_actor_type;
ALTER TABLE stock_audit_logs
    ADD CONSTRAINT chk_stock_audit_logs_actor_type CHECK (
        actor_type IN ('CUSTOMER', 'MERCHANT', 'ADMIN', 'SYSTEM', 'PAYMENT_PROVIDER')
    );

CREATE INDEX IF NOT EXISTS idx_stock_audit_logs_actor_type
    ON stock_audit_logs(actor_type);

CREATE INDEX IF NOT EXISTS idx_refund_requests_store_status_reason
    ON refund_requests(status, reason, created_at);
