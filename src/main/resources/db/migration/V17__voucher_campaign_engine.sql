-- ============================================================================
-- V16 - Voucher and campaign engine.
-- ============================================================================

ALTER TABLE ledger_accounts
    DROP CONSTRAINT IF EXISTS chk_ledger_accounts_type;
ALTER TABLE ledger_accounts
    ADD CONSTRAINT chk_ledger_accounts_type CHECK (
        account_type IN (
            'PLATFORM_CASH',
            'PLATFORM_REVENUE',
            'ESCROW',
            'MERCHANT_PAYABLE',
            'REFUND_LIABILITY',
            'PLATFORM_PROMOTION_EXPENSE'
        )
    );

CREATE TABLE IF NOT EXISTS voucher_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type VARCHAR(30) NOT NULL,
    store_id UUID REFERENCES stores(id) ON DELETE SET NULL,
    bag_id UUID REFERENCES surprise_bags(id) ON DELETE SET NULL,
    category VARCHAR(50),
    bag_type VARCHAR(30),
    diet_type VARCHAR(30),
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    discount_type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(12,2) NOT NULL,
    max_discount_amount NUMERIC(10,0),
    min_order_amount NUMERIC(10,0) NOT NULL DEFAULT 0,
    funding_source VARCHAR(30) NOT NULL,
    platform_funding_bps INTEGER NOT NULL DEFAULT 10000,
    merchant_funding_bps INTEGER NOT NULL DEFAULT 0,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    budget_limit_amount NUMERIC(14,0),
    reserved_budget_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    redeemed_budget_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    total_usage_limit INTEGER,
    per_user_limit INTEGER NOT NULL DEFAULT 1,
    reserved_count INTEGER NOT NULL DEFAULT 0,
    redeemed_count INTEGER NOT NULL DEFAULT 0,
    first_order_only BOOLEAN NOT NULL DEFAULT FALSE,
    approved_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_voucher_campaigns_owner CHECK (owner_type IN ('PLATFORM', 'MERCHANT')),
    CONSTRAINT chk_voucher_campaigns_status CHECK (
        status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'PAUSED', 'ENDED', 'CANCELLED')
    ),
    CONSTRAINT chk_voucher_campaigns_discount_type CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_voucher_campaigns_funding CHECK (funding_source IN ('PLATFORM', 'MERCHANT', 'SHARED')),
    CONSTRAINT chk_voucher_campaigns_time CHECK (ends_at > starts_at),
    CONSTRAINT chk_voucher_campaigns_amounts CHECK (
        discount_value > 0
        AND min_order_amount >= 0
        AND (max_discount_amount IS NULL OR max_discount_amount >= 0)
        AND (budget_limit_amount IS NULL OR budget_limit_amount >= 0)
        AND reserved_budget_amount >= 0
        AND redeemed_budget_amount >= 0
    ),
    CONSTRAINT chk_voucher_campaigns_limits CHECK (
        (total_usage_limit IS NULL OR total_usage_limit > 0)
        AND per_user_limit > 0
        AND reserved_count >= 0
        AND redeemed_count >= 0
    ),
    CONSTRAINT chk_voucher_campaigns_funding_bps CHECK (
        platform_funding_bps >= 0
        AND merchant_funding_bps >= 0
        AND platform_funding_bps + merchant_funding_bps = 10000
    )
);

CREATE INDEX IF NOT EXISTS idx_voucher_campaigns_status_time
    ON voucher_campaigns(status, starts_at, ends_at);
CREATE INDEX IF NOT EXISTS idx_voucher_campaigns_store_status
    ON voucher_campaigns(store_id, status);
CREATE INDEX IF NOT EXISTS idx_voucher_campaigns_owner
    ON voucher_campaigns(owner_type, funding_source, status);
CREATE INDEX IF NOT EXISTS idx_voucher_campaigns_bag
    ON voucher_campaigns(bag_id);

CREATE TABLE IF NOT EXISTS voucher_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES voucher_campaigns(id) ON DELETE CASCADE,
    code VARCHAR(80) NOT NULL UNIQUE,
    code_type VARCHAR(30) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    usage_limit INTEGER,
    reserved_count INTEGER NOT NULL DEFAULT 0,
    redeemed_count INTEGER NOT NULL DEFAULT 0,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_voucher_codes_type CHECK (code_type IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_voucher_codes_status CHECK (status IN ('ACTIVE', 'PAUSED', 'EXHAUSTED', 'EXPIRED')),
    CONSTRAINT chk_voucher_codes_limits CHECK (
        (usage_limit IS NULL OR usage_limit > 0)
        AND reserved_count >= 0
        AND redeemed_count >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_voucher_codes_campaign_status
    ON voucher_codes(campaign_id, status);
CREATE INDEX IF NOT EXISTS idx_voucher_codes_type_status
    ON voucher_codes(code_type, status);

CREATE TABLE IF NOT EXISTS user_vouchers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    campaign_id UUID NOT NULL REFERENCES voucher_campaigns(id) ON DELETE CASCADE,
    voucher_code_id UUID REFERENCES voucher_codes(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CLAIMED',
    expires_at TIMESTAMPTZ,
    reserved_order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    redeemed_order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    reserved_until TIMESTAMPTZ,
    redeemed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_user_vouchers_status CHECK (
        status IN ('CLAIMED', 'RESERVED', 'REDEEMED', 'EXPIRED', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_user_vouchers_user_status
    ON user_vouchers(user_id, status);
CREATE INDEX IF NOT EXISTS idx_user_vouchers_campaign_status
    ON user_vouchers(campaign_id, status);
CREATE INDEX IF NOT EXISTS idx_user_vouchers_code
    ON user_vouchers(voucher_code_id);

CREATE TABLE IF NOT EXISTS voucher_redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    campaign_id UUID NOT NULL REFERENCES voucher_campaigns(id) ON DELETE RESTRICT,
    voucher_code_id UUID REFERENCES voucher_codes(id) ON DELETE SET NULL,
    user_voucher_id UUID REFERENCES user_vouchers(id) ON DELETE SET NULL,
    code_snapshot VARCHAR(80),
    discount_type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(12,2) NOT NULL,
    funding_source VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    subtotal_amount NUMERIC(10,0) NOT NULL,
    discount_amount NUMERIC(10,0) NOT NULL,
    platform_funded_amount NUMERIC(10,0) NOT NULL DEFAULT 0,
    merchant_funded_amount NUMERIC(10,0) NOT NULL DEFAULT 0,
    reserved_at TIMESTAMPTZ NOT NULL,
    reserved_until TIMESTAMPTZ,
    redeemed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    reissued_at TIMESTAMPTZ,
    release_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_voucher_redemptions_discount_type CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_voucher_redemptions_funding CHECK (funding_source IN ('PLATFORM', 'MERCHANT', 'SHARED')),
    CONSTRAINT chk_voucher_redemptions_status CHECK (status IN ('RESERVED', 'REDEEMED', 'RELEASED', 'CANCELLED')),
    CONSTRAINT chk_voucher_redemptions_amounts CHECK (
        subtotal_amount >= 0
        AND discount_amount >= 0
        AND platform_funded_amount >= 0
        AND merchant_funded_amount >= 0
        AND discount_amount = platform_funded_amount + merchant_funded_amount
        AND discount_amount <= subtotal_amount
    )
);

CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_user_status
    ON voucher_redemptions(user_id, status);
CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_campaign_status
    ON voucher_redemptions(campaign_id, status);
CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_code
    ON voucher_redemptions(voucher_code_id);
