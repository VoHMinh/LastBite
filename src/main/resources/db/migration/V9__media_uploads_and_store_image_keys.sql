ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS cover_image_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS logo_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS business_license_image_key VARCHAR(500);

CREATE TABLE IF NOT EXISTS media_uploads (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    purpose VARCHAR(50) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    target_type VARCHAR(50),
    target_id UUID,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(700) NOT NULL UNIQUE,
    public_url VARCHAR(1000) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_media_uploads_owner_user_id ON media_uploads(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_media_uploads_purpose ON media_uploads(purpose);
CREATE INDEX IF NOT EXISTS idx_media_uploads_status ON media_uploads(status);
CREATE INDEX IF NOT EXISTS idx_media_uploads_target ON media_uploads(target_type, target_id);
