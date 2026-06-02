-- ============================================================================
--  V8 - Packaging information for surprise bag detail
-- ============================================================================

ALTER TABLE surprise_bags
    ADD COLUMN IF NOT EXISTS container_provided BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE surprise_bags
    ADD COLUMN IF NOT EXISTS carrier_bag_provided BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE surprise_bags
    ADD COLUMN IF NOT EXISTS packaging_note VARCHAR(500);
