-- ============================================================================
--  V7 - Diet type for surprise bags & favorite stores
-- ============================================================================

ALTER TABLE surprise_bags
    ADD COLUMN IF NOT EXISTS diet_type VARCHAR(30) NOT NULL DEFAULT 'MEAT';

ALTER TABLE surprise_bags
    ADD CONSTRAINT chk_surprise_bags_diet_type
    CHECK (diet_type IN ('MEAT', 'VEGETARIAN', 'VEGAN'));

CREATE INDEX IF NOT EXISTS idx_surprise_bags_diet_type_bag_type
    ON surprise_bags(diet_type, bag_type);

CREATE TABLE IF NOT EXISTS favorite_stores (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id   UUID        NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_favorite_stores_user_store UNIQUE (user_id, store_id)
);

CREATE INDEX IF NOT EXISTS idx_favorite_stores_user_id ON favorite_stores(user_id);
