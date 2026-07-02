-- ============================================================================
-- V17 - Discovery collections and ranking engine.
-- ============================================================================

CREATE TABLE IF NOT EXISTS platform_configs (
    config_key VARCHAR(120) PRIMARY KEY,
    config_value JSONB NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

INSERT INTO platform_configs (config_key, config_value, description)
VALUES (
    'discovery.ranking',
    '{
      "weights": {
        "distance": 0.30,
        "urgency": 0.20,
        "discount": 0.20,
        "rating": 0.15,
        "availability": 0.15
      },
      "maxRadiusKm": 5.0,
      "minReachableMinutes": 15,
      "urgencyWindowMinutes": 120,
      "minReviewsThreshold": 5,
      "defaultNeutralRatingScore": 0.8,
      "availabilityNormalizeCap": 10,
      "favoriteStoreBoost": 0.20,
      "categoryHistoryBoost": 0.15
    }'::jsonb,
    'Discovery ranking defaults and personalization boosts'
)
ON CONFLICT (config_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS discovery_collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    rule_definition JSONB,
    display_order INTEGER NOT NULL DEFAULT 0,
    max_items INTEGER NOT NULL DEFAULT 10,
    min_items_to_display INTEGER NOT NULL DEFAULT 3,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_discovery_collections_type
        CHECK (type IN ('RULE_BASED', 'CURATED_MANUAL', 'PERSONALIZED')),
    CONSTRAINT chk_discovery_collections_max_items
        CHECK (max_items BETWEEN 1 AND 50),
    CONSTRAINT chk_discovery_collections_min_items
        CHECK (min_items_to_display >= 0 AND min_items_to_display <= max_items)
);

CREATE INDEX IF NOT EXISTS idx_discovery_collections_active_order
    ON discovery_collections(is_active, display_order);

CREATE TABLE IF NOT EXISTS discovery_collection_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES discovery_collections(id) ON DELETE CASCADE,
    bag_id UUID NOT NULL REFERENCES surprise_bags(id) ON DELETE CASCADE,
    pinned_order INTEGER,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_discovery_collection_items_collection_bag UNIQUE (collection_id, bag_id),
    CONSTRAINT chk_discovery_collection_items_pinned_order
        CHECK (pinned_order IS NULL OR pinned_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_discovery_collection_items_collection
    ON discovery_collection_items(collection_id, pinned_order);

CREATE TABLE IF NOT EXISTS bag_ranking_cache (
    daily_stock_id UUID PRIMARY KEY REFERENCES bag_daily_stocks(id) ON DELETE CASCADE,
    bag_id UUID NOT NULL REFERENCES surprise_bags(id) ON DELETE CASCADE,
    rating_score NUMERIC(5,4) NOT NULL,
    discount_score NUMERIC(5,4) NOT NULL,
    urgency_score NUMERIC(5,4) NOT NULL,
    availability_score NUMERIC(5,4) NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bag_ranking_cache_rating_score CHECK (rating_score BETWEEN 0 AND 1),
    CONSTRAINT chk_bag_ranking_cache_discount_score CHECK (discount_score BETWEEN 0 AND 1),
    CONSTRAINT chk_bag_ranking_cache_urgency_score CHECK (urgency_score BETWEEN 0 AND 1),
    CONSTRAINT chk_bag_ranking_cache_availability_score CHECK (availability_score BETWEEN 0 AND 1)
);

CREATE INDEX IF NOT EXISTS idx_bag_ranking_cache_bag_id
    ON bag_ranking_cache(bag_id);

INSERT INTO discovery_collections (
    slug, title, type, rule_definition, display_order, max_items, min_items_to_display, is_active
) VALUES
    ('near_you', 'Gan ban', 'RULE_BASED',
     '{"rule":"NEAR_YOU","params":{"maxDistanceKm":5},"sort":"DISTANCE_ASC"}'::jsonb, 10, 10, 3, TRUE),
    ('last_chance', 'Sap het gio pickup', 'RULE_BASED',
     '{"rule":"LAST_CHANCE","params":{},"sort":"RANKING_DESC"}'::jsonb, 20, 10, 3, TRUE),
    ('big_discount', 'Dang giam manh', 'RULE_BASED',
     '{"rule":"BIG_DISCOUNT","params":{"minDiscountPercent":35},"sort":"DISCOUNT_DESC"}'::jsonb, 30, 10, 3, TRUE),
    ('under_30k', 'Duoi 30,000d', 'RULE_BASED',
     '{"rule":"UNDER_PRICE","params":{"maxPrice":30000},"sort":"RANKING_DESC"}'::jsonb, 40, 10, 3, TRUE),
    ('new_stores', 'Cua hang moi', 'RULE_BASED',
     '{"rule":"NEW_STORES","params":{"days":30},"sort":"STORE_CREATED_DESC"}'::jsonb, 50, 10, 3, TRUE),
    ('top_rated', 'Duoc danh gia cao', 'RULE_BASED',
     '{"rule":"TOP_RATED","params":{"minRating":4.0,"minReviews":5},"sort":"RATING_DESC"}'::jsonb, 60, 10, 3, TRUE),
    ('bestseller_today', 'Ban chay hom nay', 'RULE_BASED',
     '{"rule":"BESTSELLER_TODAY","params":{},"sort":"ORDERS_TODAY_DESC"}'::jsonb, 70, 10, 3, TRUE),
    ('recommended_for_you', 'Goi y cho ban', 'PERSONALIZED',
     '{"rule":"RECOMMENDED_FOR_YOU","params":{},"sort":"PERSONALIZED_DESC"}'::jsonb, 80, 10, 3, TRUE)
ON CONFLICT (slug) DO UPDATE SET
    title = EXCLUDED.title,
    type = EXCLUDED.type,
    rule_definition = EXCLUDED.rule_definition,
    display_order = EXCLUDED.display_order,
    max_items = EXCLUDED.max_items,
    min_items_to_display = EXCLUDED.min_items_to_display,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();
