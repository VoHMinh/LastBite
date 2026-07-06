-- V29: Update preferred_diet to match API (MEAT, VEGETARIAN, VEGAN) and add preferred_bag_type
-- Drop old check constraint (cannot alter constraint in Postgres, must drop + re-add)
ALTER TABLE user_discovery_preferences
    DROP CONSTRAINT IF EXISTS chk_user_discovery_preferences_diet;

-- Update existing rows: EAT_EVERYTHING -> MEAT, NOT_SPECIFIED -> MEAT (safe default)
UPDATE user_discovery_preferences
SET preferred_diet = 'MEAT'
WHERE preferred_diet IN ('EAT_EVERYTHING', 'NOT_SPECIFIED');

-- Re-add check constraint with new values
ALTER TABLE user_discovery_preferences
    ADD CONSTRAINT chk_user_discovery_preferences_diet
        CHECK (preferred_diet IN ('MEAT', 'VEGETARIAN', 'VEGAN'));

-- Add preferred_bag_type column
ALTER TABLE user_discovery_preferences
    ADD COLUMN IF NOT EXISTS preferred_bag_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

-- Add check constraint for preferred_bag_type
ALTER TABLE user_discovery_preferences
    ADD CONSTRAINT chk_user_discovery_preferences_bag_type
        CHECK (preferred_bag_type IN ('STANDARD', 'BREAD', 'MEAL', 'GROCERY', 'MIXED'));
