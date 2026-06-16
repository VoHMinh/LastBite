-- ============================================================================
-- V12 - Commerce-grade order lifecycle: payments, refunds, pickup, reviews,
--       merchant settlement, audit logs and store calendar overrides.
-- ============================================================================

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expired_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS picked_up_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refund_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS pickup_qr_token_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS pickup_code_hash VARCHAR(128);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_refund_status;
ALTER TABLE orders
    ADD CONSTRAINT chk_orders_refund_status CHECK (
        refund_status IN ('NONE', 'REQUESTED', 'APPROVED', 'REJECTED', 'REFUNDED', 'PARTIALLY_REFUNDED')
    );

CREATE INDEX IF NOT EXISTS idx_orders_payment_expires_at ON orders(payment_expires_at);
CREATE INDEX IF NOT EXISTS idx_orders_pickup_window ON orders(pickup_date, pickup_start_time, pickup_end_time);
CREATE INDEX IF NOT EXISTS idx_orders_refund_status ON orders(refund_status);

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    provider VARCHAR(30) NOT NULL,
    provider_order_code BIGINT NOT NULL UNIQUE,
    provider_payment_link_id VARCHAR(120),
    amount NUMERIC(10,0) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    checkout_url VARCHAR(1000),
    qr_code TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    failure_reason VARCHAR(1000),
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    raw_provider_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_payments_provider CHECK (provider IN ('PAYOS', 'FAKE')),
    CONSTRAINT chk_payments_status CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED', 'PARTIALLY_REFUNDED')
    ),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_expires_at ON payments(expires_at);
CREATE INDEX IF NOT EXISTS idx_payments_provider_link ON payments(provider, provider_payment_link_id);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_transaction_id VARCHAR(120),
    amount NUMERIC(10,0) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL,
    provider_code VARCHAR(30),
    provider_description VARCHAR(500),
    paid_at TIMESTAMPTZ,
    raw_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_payment_transactions_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_payment_transactions_amount CHECK (amount >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_transactions_provider_ref
    ON payment_transactions(provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_transactions_payment_id ON payment_transactions(payment_id);

CREATE TABLE IF NOT EXISTS payment_webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(30) NOT NULL,
    event_key VARCHAR(220) NOT NULL UNIQUE,
    provider_order_code BIGINT,
    payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    signature VARCHAR(255),
    payload TEXT NOT NULL,
    valid_signature BOOLEAN NOT NULL DEFAULT FALSE,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_payment_webhooks_provider CHECK (provider IN ('PAYOS', 'FAKE'))
);

CREATE INDEX IF NOT EXISTS idx_payment_webhooks_provider_order_code ON payment_webhooks(provider_order_code);
CREATE INDEX IF NOT EXISTS idx_payment_webhooks_processed ON payment_webhooks(processed);

CREATE TABLE IF NOT EXISTS payment_gateway_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    provider VARCHAR(30) NOT NULL,
    request_type VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_payload TEXT,
    response_payload TEXT,
    provider_reference VARCHAR(160),
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_payment_gateway_requests_type CHECK (request_type IN ('CREATE_PAYMENT_LINK', 'CANCEL_PAYMENT_LINK', 'CREATE_PAYOUT', 'CREATE_REFUND')),
    CONSTRAINT chk_payment_gateway_requests_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_gateway_requests_provider_idem
    ON payment_gateway_requests(provider, idempotency_key);

CREATE TABLE IF NOT EXISTS order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_type VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    reason VARCHAR(500),
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_status_history_actor CHECK (actor_type IN ('CUSTOMER', 'MERCHANT', 'ADMIN', 'SYSTEM', 'PAYMENT_PROVIDER'))
);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order_id ON order_status_history(order_id);
CREATE INDEX IF NOT EXISTS idx_order_status_history_created_at ON order_status_history(created_at);

CREATE TABLE IF NOT EXISTS pickup_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(30) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_pickup_events_type CHECK (event_type IN ('CONFIRMED', 'REJECTED', 'NO_SHOW')),
    CONSTRAINT chk_pickup_events_channel CHECK (channel IN ('MANUAL_CODE', 'QR_SCAN', 'SYSTEM'))
);

CREATE INDEX IF NOT EXISTS idx_pickup_events_order_id ON pickup_events(order_id);
CREATE INDEX IF NOT EXISTS idx_pickup_events_store_created ON pickup_events(store_id, created_at DESC);

CREATE TABLE IF NOT EXISTS refund_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    requested_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reason VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    requested_amount NUMERIC(10,0) NOT NULL,
    approved_amount NUMERIC(10,0),
    description TEXT,
    decision_note VARCHAR(1000),
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    auto_created BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_refund_requests_reason CHECK (
        reason IN ('STORE_CANCELLED', 'STORE_NO_STOCK', 'PAYMENT_AFTER_EXPIRY', 'QUALITY_ISSUE',
                   'ALLERGEN_OR_LABELING', 'QUANTITY_SHORTAGE', 'PLATFORM_ERROR', 'CUSTOMER_COMPLAINT', 'OTHER')
    ),
    CONSTRAINT chk_refund_requests_status CHECK (
        status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'PROCESSING', 'REFUNDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_refund_requests_amount CHECK (requested_amount >= 0 AND (approved_amount IS NULL OR approved_amount >= 0))
);

CREATE INDEX IF NOT EXISTS idx_refund_requests_order_id ON refund_requests(order_id);
CREATE INDEX IF NOT EXISTS idx_refund_requests_status ON refund_requests(status);
CREATE INDEX IF NOT EXISTS idx_refund_requests_created_at ON refund_requests(created_at);

CREATE TABLE IF NOT EXISTS refund_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_request_id UUID NOT NULL REFERENCES refund_requests(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL DEFAULT 'PAYOS',
    provider_reference VARCHAR(160),
    amount NUMERIC(10,0) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    method VARCHAR(30) NOT NULL DEFAULT 'MANUAL_BANK_TRANSFER',
    raw_payload TEXT,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_refund_transactions_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_refund_transactions_method CHECK (method IN ('ORIGINAL_SOURCE', 'MANUAL_BANK_TRANSFER', 'PAYOS_PAYOUT')),
    CONSTRAINT chk_refund_transactions_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_refund_transactions_request_id ON refund_transactions(refund_request_id);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id UUID,
    ip_address VARCHAR(80),
    user_agent VARCHAR(500),
    reason VARCHAR(1000),
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_actor_created ON admin_audit_logs(actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_target ON admin_audit_logs(target_type, target_id);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status_available ON outbox_events(status, available_at);

CREATE TABLE IF NOT EXISTS ledger_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type VARCHAR(30) NOT NULL,
    owner_id UUID,
    account_type VARCHAR(40) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    balance NUMERIC(14,0) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_ledger_accounts_owner_type UNIQUE (owner_type, owner_id, account_type, currency),
    CONSTRAINT chk_ledger_accounts_owner_type CHECK (owner_type IN ('PLATFORM', 'MERCHANT', 'STORE', 'CUSTOMER')),
    CONSTRAINT chk_ledger_accounts_type CHECK (
        account_type IN ('PLATFORM_CASH', 'PLATFORM_REVENUE', 'ESCROW', 'MERCHANT_PAYABLE', 'REFUND_LIABILITY')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_accounts_null_owner
    ON ledger_accounts(owner_type, account_type, currency)
    WHERE owner_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_accounts_non_null_owner
    ON ledger_accounts(owner_type, owner_id, account_type, currency)
    WHERE owner_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES ledger_accounts(id) ON DELETE RESTRICT,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    refund_request_id UUID REFERENCES refund_requests(id) ON DELETE SET NULL,
    settlement_id UUID,
    entry_type VARCHAR(50) NOT NULL,
    entry_direction VARCHAR(10) NOT NULL,
    amount NUMERIC(14,0) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    available_at TIMESTAMPTZ,
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_ledger_entries_direction CHECK (entry_direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_ledger_entries_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_order_id ON ledger_entries(order_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_settlement ON ledger_entries(settlement_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_available ON ledger_entries(available_at);

CREATE TABLE IF NOT EXISTS platform_commissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
    payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    gross_amount NUMERIC(10,0) NOT NULL,
    platform_fee_amount NUMERIC(10,0) NOT NULL,
    merchant_net_amount NUMERIC(10,0) NOT NULL,
    rate_bps INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_platform_commissions_status CHECK (status IN ('PENDING', 'EARNED', 'REVERSED'))
);

CREATE TABLE IF NOT EXISTS merchant_settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_profile_id UUID NOT NULL REFERENCES merchant_business_profiles(id) ON DELETE RESTRICT,
    store_id UUID REFERENCES stores(id) ON DELETE SET NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    gross_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    commission_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    refund_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    net_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_merchant_settlements_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PAYOUT_PROCESSING', 'PAID', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_merchant_settlements_business_status ON merchant_settlements(business_profile_id, status);
CREATE INDEX IF NOT EXISTS idx_merchant_settlements_store_status ON merchant_settlements(store_id, status);

ALTER TABLE ledger_entries
    DROP CONSTRAINT IF EXISTS fk_ledger_entries_settlement;
ALTER TABLE ledger_entries
    ADD CONSTRAINT fk_ledger_entries_settlement
    FOREIGN KEY (settlement_id) REFERENCES merchant_settlements(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS store_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id UUID NOT NULL REFERENCES merchant_settlements(id) ON DELETE CASCADE,
    store_id UUID REFERENCES stores(id) ON DELETE SET NULL,
    bank_account_id UUID REFERENCES merchant_bank_accounts(id) ON DELETE SET NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'PAYOS',
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    amount NUMERIC(14,0) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    provider_payout_id VARCHAR(160),
    provider_transaction_id VARCHAR(160),
    failure_reason VARCHAR(1000),
    requested_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_store_payouts_status CHECK (status IN ('PENDING', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_store_payouts_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_store_payouts_settlement_id ON store_payouts(settlement_id);
CREATE INDEX IF NOT EXISTS idx_store_payouts_status ON store_payouts(status);

CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE RESTRICT,
    bag_id UUID NOT NULL REFERENCES surprise_bags(id) ON DELETE RESTRICT,
    overall_rating INTEGER NOT NULL,
    collection_rating INTEGER NOT NULL,
    quality_rating INTEGER NOT NULL,
    variety_rating INTEGER NOT NULL,
    quantity_rating INTEGER NOT NULL,
    comment TEXT,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    hidden_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_reviews_scores CHECK (
        overall_rating BETWEEN 1 AND 5
        AND collection_rating BETWEEN 1 AND 5
        AND quality_rating BETWEEN 1 AND 5
        AND variety_rating BETWEEN 1 AND 5
        AND quantity_rating BETWEEN 1 AND 5
    )
);

CREATE INDEX IF NOT EXISTS idx_reviews_store_visible ON reviews(store_id, visible);
CREATE INDEX IF NOT EXISTS idx_reviews_user_id ON reviews(user_id);

CREATE TABLE IF NOT EXISTS review_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    media_upload_id UUID NOT NULL UNIQUE REFERENCES media_uploads(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS review_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    reported_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    resolution_note VARCHAR(1000),
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_review_reports_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_review_reports_status ON review_reports(status);

CREATE TABLE IF NOT EXISTS store_rating_summaries (
    store_id UUID PRIMARY KEY REFERENCES stores(id) ON DELETE CASCADE,
    review_count INTEGER NOT NULL DEFAULT 0,
    recent_review_count INTEGER NOT NULL DEFAULT 0,
    overall_rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
    collection_rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
    quality_rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
    variety_rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
    quantity_rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS store_closure_days (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    closed_date DATE NOT NULL,
    reason VARCHAR(500),
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_store_closure_day UNIQUE (store_id, closed_date)
);

CREATE INDEX IF NOT EXISTS idx_store_closure_days_store_date ON store_closure_days(store_id, closed_date);

CREATE TABLE IF NOT EXISTS store_special_hours (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    special_date DATE NOT NULL,
    open_time TIME,
    close_time TIME,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(500),
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_store_special_hours UNIQUE (store_id, special_date),
    CONSTRAINT chk_store_special_hours_time CHECK (
        is_closed = TRUE OR (open_time IS NOT NULL AND close_time IS NOT NULL AND close_time > open_time)
    )
);

CREATE INDEX IF NOT EXISTS idx_store_special_hours_store_date ON store_special_hours(store_id, special_date);
