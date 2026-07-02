-- Account deletion workflow for Google Play compliance and marketplace audit safety.

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_status;

ALTER TABLE users
    ADD CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING_DELETION', 'DELETED', 'BANNED'));

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deletion_scheduled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS anonymized_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS account_deletion_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    request_email VARCHAR(255),
    request_phone VARCHAR(30),
    requester_type VARCHAR(30) NOT NULL,
    source VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reason TEXT,
    blocker_summary TEXT,
    token_hash VARCHAR(255) UNIQUE,
    token_expires_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    scheduled_deletion_at TIMESTAMPTZ,
    finalized_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    admin_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_account_deletion_requester_type CHECK (
        requester_type IN ('CUSTOMER', 'MERCHANT_OWNER', 'STORE_MEMBER', 'ADMIN')
    ),
    CONSTRAINT chk_account_deletion_source CHECK (
        source IN ('IN_APP', 'WEB')
    ),
    CONSTRAINT chk_account_deletion_status CHECK (
        status IN (
            'PENDING_VERIFICATION',
            'PENDING_REVIEW',
            'BLOCKED',
            'PENDING_FINALIZATION',
            'FINALIZED',
            'CANCELLED',
            'REJECTED'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_account_deletion_user_status
    ON account_deletion_requests(user_id, status);

CREATE INDEX IF NOT EXISTS idx_account_deletion_status
    ON account_deletion_requests(status);

CREATE INDEX IF NOT EXISTS idx_account_deletion_token_hash
    ON account_deletion_requests(token_hash);

CREATE INDEX IF NOT EXISTS idx_account_deletion_scheduled
    ON account_deletion_requests(scheduled_deletion_at);
