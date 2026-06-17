ALTER TABLE refund_requests
    ADD COLUMN IF NOT EXISTS refund_bank_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS refund_bank_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS refund_account_holder_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS refund_account_number_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS refund_account_number_last4 VARCHAR(4);

ALTER TABLE refund_transactions
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160),
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payment_gateway_requests
    ADD COLUMN IF NOT EXISTS refund_transaction_id UUID;

ALTER TABLE payment_gateway_requests
    DROP CONSTRAINT IF EXISTS fk_payment_gateway_requests_refund_transaction;
ALTER TABLE payment_gateway_requests
    ADD CONSTRAINT fk_payment_gateway_requests_refund_transaction
    FOREIGN KEY (refund_transaction_id) REFERENCES refund_transactions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_orders_user_created
    ON orders(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_store_pickup_status
    ON orders(store_id, pickup_date, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_refund_requests_status_created
    ON refund_requests(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_refund_transactions_worker
    ON refund_transactions(status, method, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_transactions_idempotency
    ON refund_transactions(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_gateway_requests_refund_transaction
    ON payment_gateway_requests(refund_transaction_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_action_created
    ON admin_audit_logs(action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_created
    ON admin_audit_logs(created_at DESC);
