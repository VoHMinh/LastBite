-- Merchant platform architecture:
-- platform roles, multi-store business profiles, store memberships and review versions.

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    scope VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_roles_scope CHECK (scope IN ('PLATFORM', 'STORE'))
);

INSERT INTO roles (code, scope) VALUES
    ('CUSTOMER', 'PLATFORM'),
    ('MERCHANT_OWNER', 'PLATFORM'),
    ('ADMIN', 'PLATFORM'),
    ('MANAGER', 'STORE'),
    ('STAFF', 'STORE');

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

ALTER TABLE users
    ADD COLUMN username VARCHAR(80),
    ADD COLUMN account_type VARCHAR(30) NOT NULL DEFAULT 'PLATFORM',
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_users_username_lower
    ON users (LOWER(username))
    WHERE username IS NOT NULL;

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = CASE
    WHEN u.role = 'STORE_OWNER' THEN 'MERCHANT_OWNER'
    ELSE u.role
END;

DROP INDEX IF EXISTS idx_users_role;
ALTER TABLE users DROP COLUMN role;

ALTER TABLE users
    ADD CONSTRAINT chk_users_account_type
    CHECK (account_type IN ('PLATFORM', 'STORE_MEMBER'));

CREATE TABLE merchant_business_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    legal_type VARCHAR(40) NOT NULL,
    legal_name VARCHAR(255),
    representative_full_name VARCHAR(255) NOT NULL,
    representative_phone VARCHAR(20),
    representative_email VARCHAR(255),
    identity_document_type VARCHAR(30),
    identity_document_number VARCHAR(100),
    tax_code VARCHAR(50),
    registration_number VARCHAR(100),
    parent_company_name VARCHAR(255),
    parent_company_tax_code VARCHAR(50),
    business_address TEXT,
    review_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    rejection_reason VARCHAR(1000),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_business_legal_type CHECK (
        legal_type IN ('INDIVIDUAL', 'HOUSEHOLD_BUSINESS', 'COMPANY', 'COMPANY_BRANCH')
    ),
    CONSTRAINT chk_business_review_status CHECK (
        review_status IN ('DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')
    )
);

INSERT INTO merchant_business_profiles (
    owner_user_id,
    legal_type,
    representative_full_name,
    representative_phone,
    representative_email,
    review_status,
    approved_at
)
SELECT DISTINCT
    u.id,
    'INDIVIDUAL',
    u.full_name,
    u.phone,
    u.email,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM stores s
            WHERE s.owner_id = u.id AND s.verification_status = 'VERIFIED'
        ) THEN 'APPROVED'
        ELSE 'DRAFT'
    END,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM stores s
            WHERE s.owner_id = u.id AND s.verification_status = 'VERIFIED'
        ) THEN NOW()
        ELSE NULL
    END
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id AND r.code = 'MERCHANT_OWNER';

ALTER TABLE stores DROP CONSTRAINT IF EXISTS stores_owner_id_key;
ALTER TABLE stores RENAME COLUMN owner_id TO created_by_user_id;
ALTER INDEX IF EXISTS idx_stores_owner_id RENAME TO idx_stores_created_by_user_id;

ALTER TABLE stores
    ADD COLUMN business_profile_id UUID REFERENCES merchant_business_profiles(id) ON DELETE RESTRICT,
    ADD COLUMN pickup_instructions TEXT,
    ADD COLUMN storefront_image_url VARCHAR(500),
    ADD COLUMN storefront_image_key VARCHAR(500),
    ADD COLUMN menu_image_url VARCHAR(500),
    ADD COLUMN menu_image_key VARCHAR(500);

UPDATE stores s
SET business_profile_id = bp.id
FROM merchant_business_profiles bp
WHERE bp.owner_user_id = s.created_by_user_id;

ALTER TABLE stores ALTER COLUMN business_profile_id SET NOT NULL;
CREATE INDEX idx_stores_business_profile_id ON stores(business_profile_id);

ALTER TABLE stores DROP CONSTRAINT IF EXISTS chk_store_status;
ALTER TABLE stores DROP CONSTRAINT IF EXISTS chk_store_verification;
ALTER TABLE stores
    ADD CONSTRAINT chk_store_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED', 'SUSPENDED')),
    ADD CONSTRAINT chk_store_verification
        CHECK (verification_status IN ('DRAFT', 'PENDING', 'CHANGES_REQUESTED', 'VERIFIED', 'REJECTED'));

CREATE TABLE merchant_business_profile_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_profile_id UUID NOT NULL REFERENCES merchant_business_profiles(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    UNIQUE (business_profile_id, version_number),
    CONSTRAINT chk_business_version_review_status CHECK (
        review_status IN ('DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')
    )
);

CREATE TABLE merchant_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_profile_id UUID NOT NULL REFERENCES merchant_business_profiles(id) ON DELETE CASCADE,
    store_id UUID REFERENCES stores(id) ON DELETE CASCADE,
    bank_code VARCHAR(30) NOT NULL,
    bank_name VARCHAR(150) NOT NULL,
    account_holder_name VARCHAR(255) NOT NULL,
    account_number_encrypted TEXT NOT NULL,
    account_number_last4 VARCHAR(4) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_bank_verification_status CHECK (
        verification_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')
    )
);

CREATE UNIQUE INDEX uq_business_default_bank_account
    ON merchant_bank_accounts(business_profile_id)
    WHERE is_default = TRUE AND store_id IS NULL;

CREATE TABLE store_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    UNIQUE (store_id, version_number),
    CONSTRAINT chk_store_version_review_status CHECK (
        review_status IN ('DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')
    )
);

CREATE TABLE store_review_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    business_profile_version_id UUID REFERENCES merchant_business_profile_versions(id),
    store_version_id UUID REFERENCES store_versions(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    submitted_by UUID NOT NULL REFERENCES users(id),
    submitted_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    decision_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_store_review_application_status CHECK (
        status IN ('DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')
    )
);

CREATE INDEX idx_store_review_applications_status
    ON store_review_applications(status, submitted_at DESC);

CREATE TABLE merchant_store_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    UNIQUE (store_id, user_id),
    CONSTRAINT chk_store_member_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED'))
);

CREATE INDEX idx_store_members_store ON merchant_store_members(store_id);

CREATE TABLE merchant_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_profile_id UUID REFERENCES merchant_business_profiles(id) ON DELETE CASCADE,
    store_id UUID REFERENCES stores(id) ON DELETE CASCADE,
    review_application_id UUID REFERENCES store_review_applications(id) ON DELETE SET NULL,
    media_upload_id UUID NOT NULL UNIQUE REFERENCES media_uploads(id) ON DELETE RESTRICT,
    document_type VARCHAR(60) NOT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    expires_at DATE,
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_document_target CHECK (
        business_profile_id IS NOT NULL OR store_id IS NOT NULL
    ),
    CONSTRAINT chk_document_review_status CHECK (
        review_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')
    )
);

CREATE TABLE review_feedback_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_application_id UUID NOT NULL REFERENCES store_review_applications(id) ON DELETE CASCADE,
    section VARCHAR(50) NOT NULL,
    field_path VARCHAR(150) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

ALTER TABLE media_uploads
    ALTER COLUMN public_url DROP NOT NULL,
    ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_business_profiles_review_status ON merchant_business_profiles(review_status);
