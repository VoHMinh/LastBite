CREATE TABLE IF NOT EXISTS user_discovery_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    preferred_diet VARCHAR(30) NOT NULL DEFAULT 'NOT_SPECIFIED',
    default_location_label VARCHAR(255),
    default_lat DOUBLE PRECISION,
    default_lng DOUBLE PRECISION,
    default_radius_km DOUBLE PRECISION,
    onboarding_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_user_discovery_preferences_diet
        CHECK (preferred_diet IN ('EAT_EVERYTHING', 'VEGETARIAN', 'VEGAN', 'NOT_SPECIFIED')),
    CONSTRAINT chk_user_discovery_preferences_onboarding
        CHECK (onboarding_status IN ('NOT_STARTED', 'COMPLETED', 'SKIPPED')),
    CONSTRAINT chk_user_discovery_preferences_lat
        CHECK (default_lat IS NULL OR (default_lat >= -90 AND default_lat <= 90)),
    CONSTRAINT chk_user_discovery_preferences_lng
        CHECK (default_lng IS NULL OR (default_lng >= -180 AND default_lng <= 180)),
    CONSTRAINT chk_user_discovery_preferences_radius
        CHECK (default_radius_km IS NULL OR (default_radius_km > 0 AND default_radius_km <= 50)),
    CONSTRAINT chk_user_discovery_preferences_location_complete
        CHECK (
            (default_location_label IS NULL AND default_lat IS NULL AND default_lng IS NULL AND default_radius_km IS NULL)
            OR
            (default_location_label IS NOT NULL AND default_lat IS NOT NULL AND default_lng IS NOT NULL AND default_radius_km IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_user_discovery_preferences_user
    ON user_discovery_preferences(user_id);

CREATE TABLE IF NOT EXISTS user_preferred_collection_times (
    preference_id UUID NOT NULL REFERENCES user_discovery_preferences(id) ON DELETE CASCADE,
    slot VARCHAR(30) NOT NULL,
    PRIMARY KEY (preference_id, slot),
    CONSTRAINT chk_user_preferred_collection_times_slot
        CHECK (slot IN ('EARLY_MORNING', 'LATE_MORNING', 'MIDDAY', 'AFTERNOON', 'EVENING', 'LATE_NIGHT'))
);

CREATE INDEX IF NOT EXISTS idx_user_preferred_collection_times_slot
    ON user_preferred_collection_times(slot);
