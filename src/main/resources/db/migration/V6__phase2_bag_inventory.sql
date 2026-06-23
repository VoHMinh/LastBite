-- ============================================================================
--  V6 - Phase 2: Surprise Bags, Platform Pricing Tiers & Daily Inventory
--  Tables: bag_price_tiers, surprise_bags, bag_daily_stocks, orders, stock_audit_logs
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS bag_price_tiers (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    category                 VARCHAR(50)   NOT NULL,
    bag_size                 VARCHAR(30)   NOT NULL,
    minimum_value            NUMERIC(10,0) NOT NULL,
    base_sale_price          NUMERIC(10,0) NOT NULL,
    dynamic_min_price        NUMERIC(10,0) NOT NULL,
    dynamic_max_price        NUMERIC(10,0) NOT NULL,
    platform_fee             NUMERIC(10,0) NOT NULL DEFAULT 4000,
    active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_bag_price_tiers_category_size UNIQUE (category, bag_size),
    CONSTRAINT chk_bag_price_tiers_category CHECK (
        category IN ('BAKERY', 'RESTAURANT', 'CAFE', 'GROCERY', 'CONVENIENCE')
    ),
    CONSTRAINT chk_bag_price_tiers_size CHECK (bag_size IN ('MINI', 'SMALL', 'STANDARD', 'LARGE')),
    CONSTRAINT chk_bag_price_tiers_values CHECK (
        minimum_value > 0
        AND dynamic_min_price > 0
        AND base_sale_price > 0
        AND dynamic_max_price > 0
        AND dynamic_min_price <= base_sale_price
        AND base_sale_price <= dynamic_max_price
        AND dynamic_max_price < minimum_value
        AND platform_fee >= 0
    )
);

-- Some VPS databases may already have this table from Hibernate/schema previews.
-- CREATE TABLE IF NOT EXISTS then skips the definition above, so repair the
-- defaults needed by raw SQL seed data before inserting the pricing tiers.
ALTER TABLE IF EXISTS bag_price_tiers
    ALTER COLUMN id SET DEFAULT gen_random_uuid(),
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at SET DEFAULT NOW();

UPDATE bag_price_tiers
SET id = gen_random_uuid()
WHERE id IS NULL;

UPDATE bag_price_tiers
SET created_at = COALESCE(created_at, NOW()),
    updated_at = COALESCE(updated_at, NOW())
WHERE created_at IS NULL OR updated_at IS NULL;

ALTER TABLE IF EXISTS bag_price_tiers
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_bag_price_tiers_category_size
    ON bag_price_tiers(category, bag_size);

CREATE INDEX IF NOT EXISTS idx_bag_price_tiers_active ON bag_price_tiers(active);

INSERT INTO bag_price_tiers (
    id, category, bag_size, minimum_value, base_sale_price,
    dynamic_min_price, dynamic_max_price, platform_fee, active, created_at, updated_at
) VALUES
    (gen_random_uuid(), 'BAKERY', 'MINI', 50000, 19000, 17000, 23000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'BAKERY', 'SMALL', 75000, 29000, 25000, 35000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'BAKERY', 'STANDARD', 100000, 39000, 35000, 45000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'BAKERY', 'LARGE', 150000, 59000, 52000, 69000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'CAFE', 'MINI', 50000, 19000, 17000, 23000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'CAFE', 'SMALL', 75000, 29000, 25000, 35000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'CAFE', 'STANDARD', 100000, 39000, 35000, 45000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'CAFE', 'LARGE', 150000, 59000, 52000, 69000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'RESTAURANT', 'MINI', 70000, 29000, 25000, 35000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'RESTAURANT', 'SMALL', 100000, 39000, 35000, 45000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'RESTAURANT', 'STANDARD', 150000, 59000, 52000, 69000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'RESTAURANT', 'LARGE', 220000, 89000, 79000, 99000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'GROCERY', 'MINI', 60000, 24000, 21000, 29000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'GROCERY', 'SMALL', 90000, 35000, 31000, 42000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'GROCERY', 'STANDARD', 130000, 49000, 44000, 59000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'GROCERY', 'LARGE', 200000, 79000, 69000, 89000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'CONVENIENCE', 'MINI', 60000, 24000, 21000, 29000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'CONVENIENCE', 'SMALL', 90000, 35000, 31000, 42000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'CONVENIENCE', 'STANDARD', 130000, 49000, 44000, 59000, 4000, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'CONVENIENCE', 'LARGE', 200000, 79000, 69000, 89000, 4000, TRUE, NOW(), NOW())
ON CONFLICT (category, bag_size) DO NOTHING;

CREATE TABLE IF NOT EXISTS surprise_bags (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id                 UUID          NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    name                     VARCHAR(255)  NOT NULL,
    description              TEXT,
    bag_type                 VARCHAR(30)   NOT NULL DEFAULT 'STANDARD',
    category                 VARCHAR(50)   NOT NULL,
    bag_size                 VARCHAR(30)   NOT NULL,
    photos                   TEXT[]        NOT NULL DEFAULT ARRAY[]::TEXT[],
    minimum_value            NUMERIC(10,0) NOT NULL,
    base_sale_price          NUMERIC(10,0) NOT NULL,
    dynamic_min_price        NUMERIC(10,0) NOT NULL,
    dynamic_max_price        NUMERIC(10,0) NOT NULL,
    dynamic_pricing_enabled  BOOLEAN       NOT NULL DEFAULT TRUE,
    platform_fee             NUMERIC(10,0) NOT NULL DEFAULT 4000,
    max_per_order            INTEGER       NOT NULL DEFAULT 1,
    pickup_start_time        TIME          NOT NULL,
    pickup_end_time          TIME          NOT NULL,
    available_days           INTEGER[]     NOT NULL DEFAULT ARRAY[0,1,2,3,4,5,6],
    status                   VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    version                  INTEGER       NOT NULL DEFAULT 1,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_surprise_bags_type CHECK (bag_type IN ('STANDARD', 'BREAD', 'MEAL', 'GROCERY', 'MIXED')),
    CONSTRAINT chk_surprise_bags_category CHECK (
        category IN ('BAKERY', 'RESTAURANT', 'CAFE', 'GROCERY', 'CONVENIENCE')
    ),
    CONSTRAINT chk_surprise_bags_size CHECK (bag_size IN ('MINI', 'SMALL', 'STANDARD', 'LARGE')),
    CONSTRAINT chk_surprise_bags_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT chk_surprise_bags_price_snapshot CHECK (
        minimum_value > 0
        AND dynamic_min_price > 0
        AND base_sale_price > 0
        AND dynamic_max_price > 0
        AND dynamic_min_price <= base_sale_price
        AND base_sale_price <= dynamic_max_price
        AND dynamic_max_price < minimum_value
        AND platform_fee >= 0
    ),
    CONSTRAINT chk_surprise_bags_max_per_order CHECK (max_per_order BETWEEN 1 AND 3),
    CONSTRAINT chk_surprise_bags_pickup_window CHECK (
        EXTRACT(EPOCH FROM (pickup_end_time - pickup_start_time)) BETWEEN 1800 AND 14400
    ),
    CONSTRAINT chk_surprise_bags_available_days_not_empty CHECK (array_length(available_days, 1) >= 1),
    CONSTRAINT chk_surprise_bags_available_days_range CHECK (
        available_days <@ ARRAY[0,1,2,3,4,5,6]
    )
);

CREATE INDEX IF NOT EXISTS idx_surprise_bags_store_id ON surprise_bags(store_id);
CREATE INDEX IF NOT EXISTS idx_surprise_bags_store_status ON surprise_bags(store_id, status);
CREATE INDEX IF NOT EXISTS idx_surprise_bags_category_size ON surprise_bags(category, bag_size);
CREATE INDEX IF NOT EXISTS idx_surprise_bags_pickup_window ON surprise_bags(pickup_start_time, pickup_end_time);

CREATE TABLE IF NOT EXISTS bag_daily_stocks (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    bag_id              UUID         NOT NULL REFERENCES surprise_bags(id) ON DELETE CASCADE,
    store_id            UUID         NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    date                DATE         NOT NULL,
    quantity            INTEGER      NOT NULL DEFAULT 0,
    reserved            INTEGER      NOT NULL DEFAULT 0,
    sold                INTEGER      NOT NULL DEFAULT 0,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    version             INTEGER      NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_bag_daily_stocks_bag_date UNIQUE (bag_id, date),
    CONSTRAINT chk_bag_daily_stocks_status CHECK (status IN ('ACTIVE', 'SOLD_OUT', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_bag_daily_stocks_quantity CHECK (quantity >= 0 AND quantity <= 50),
    CONSTRAINT chk_bag_daily_stocks_reserved CHECK (reserved >= 0),
    CONSTRAINT chk_bag_daily_stocks_sold CHECK (sold >= 0),
    CONSTRAINT chk_bag_daily_stocks_available CHECK (reserved + sold <= quantity)
);

CREATE INDEX IF NOT EXISTS idx_bag_daily_stocks_store_date_status ON bag_daily_stocks(store_id, date, status);
CREATE INDEX IF NOT EXISTS idx_bag_daily_stocks_date ON bag_daily_stocks(date);
CREATE INDEX IF NOT EXISTS idx_bag_daily_stocks_date_status ON bag_daily_stocks(date, status);
CREATE INDEX IF NOT EXISTS idx_bag_daily_stocks_store_date ON bag_daily_stocks(store_id, date);

CREATE TABLE IF NOT EXISTS orders (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number        VARCHAR(30)   NOT NULL UNIQUE,
    user_id             UUID          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    store_id            UUID          NOT NULL REFERENCES stores(id) ON DELETE RESTRICT,
    bag_id              UUID          NOT NULL REFERENCES surprise_bags(id) ON DELETE RESTRICT,
    daily_stock_id      UUID          NOT NULL REFERENCES bag_daily_stocks(id) ON DELETE RESTRICT,
    quantity            INTEGER       NOT NULL DEFAULT 1,
    unit_price          NUMERIC(10,0) NOT NULL,
    platform_fee        NUMERIC(10,0) NOT NULL,
    subtotal            NUMERIC(10,0) NOT NULL,
    discount_amount     NUMERIC(10,0) NOT NULL DEFAULT 0,
    final_amount        NUMERIC(10,0) NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'PENDING_PAYMENT',
    pickup_code         VARCHAR(20)   NOT NULL UNIQUE,
    pickup_date         DATE          NOT NULL,
    pickup_start_time   TIME          NOT NULL,
    pickup_end_time     TIME          NOT NULL,
    reserved_until      TIMESTAMPTZ   NOT NULL,
    idempotency_key     VARCHAR(100)  NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_orders_quantity CHECK (quantity BETWEEN 1 AND 3),
    CONSTRAINT chk_orders_amounts CHECK (
        unit_price >= 0
        AND platform_fee >= 0
        AND subtotal >= 0
        AND discount_amount >= 0
        AND final_amount >= 0
    ),
    CONSTRAINT chk_orders_status CHECK (
        status IN ('PENDING_PAYMENT', 'PAID', 'READY_FOR_PICKUP', 'PICKED_UP', 'EXPIRED', 'CANCELLED', 'REFUNDED')
    )
);

CREATE INDEX IF NOT EXISTS idx_orders_order_number ON orders(order_number);
CREATE INDEX IF NOT EXISTS idx_orders_idempotency_key ON orders(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_store_id ON orders(store_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_reserved_until ON orders(reserved_until);
CREATE INDEX IF NOT EXISTS idx_orders_pickup_date ON orders(pickup_date);

CREATE TABLE IF NOT EXISTS stock_audit_logs (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    bag_id            UUID        NOT NULL REFERENCES surprise_bags(id) ON DELETE CASCADE,
    daily_stock_id    UUID        REFERENCES bag_daily_stocks(id) ON DELETE SET NULL,
    actor_id          UUID        REFERENCES users(id) ON DELETE SET NULL,
    action            VARCHAR(40) NOT NULL,
    delta             INTEGER     NOT NULL,
    quantity_before   INTEGER     NOT NULL,
    quantity_after    INTEGER     NOT NULL,
    reason            TEXT,
    order_id          UUID        REFERENCES orders(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_stock_audit_logs_action CHECK (
        action IN ('STOCK_ADD', 'STOCK_REDUCE', 'STOCK_SET', 'RESERVE', 'RESERVE_CANCEL', 'SELL', 'EXPIRE_UNSOLD')
    )
);

CREATE INDEX IF NOT EXISTS idx_stock_audit_logs_bag_id ON stock_audit_logs(bag_id);
CREATE INDEX IF NOT EXISTS idx_stock_audit_logs_daily_stock_id ON stock_audit_logs(daily_stock_id);
CREATE INDEX IF NOT EXISTS idx_stock_audit_logs_order_id ON stock_audit_logs(order_id);
CREATE INDEX IF NOT EXISTS idx_stock_audit_logs_created_at ON stock_audit_logs(created_at);
