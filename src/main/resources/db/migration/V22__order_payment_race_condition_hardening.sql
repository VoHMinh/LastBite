ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS orders_idempotency_key_key;

DROP INDEX IF EXISTS idx_orders_idempotency_key;

CREATE INDEX IF NOT EXISTS idx_orders_idempotency_key
    ON orders(idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_user_idempotency
    ON orders(user_id, idempotency_key);
