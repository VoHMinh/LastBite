-- Repair seed pickup data that may have escaped V28 when the database timezone
-- differs from the application timezone. This only touches deterministic demo
-- records, never real customer orders.

UPDATE notification_devices
SET
    is_active = FALSE,
    updated_at = NOW()
WHERE device_token LIKE 'seed-device-token-%'
  AND is_active = TRUE;

WITH app_time AS (
    SELECT ((NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE + 1) AS target_pickup_date
),
seed_orders AS (
    SELECT
        o.id,
        o.bag_id,
        app_time.target_pickup_date
    FROM orders o
    CROSS JOIN app_time
    WHERE (o.order_number LIKE 'LB-PLAY-%' OR o.order_number LIKE 'LB-DEMO-%')
      AND o.status IN ('PAID', 'READY_FOR_PICKUP')
      AND o.pickup_date <= app_time.target_pickup_date
)
UPDATE orders o
SET
    pickup_date = seed_orders.target_pickup_date,
    daily_stock_id = COALESCE(stock.id, o.daily_stock_id),
    reserved_until = GREATEST(o.reserved_until, NOW() + INTERVAL '30 minutes'),
    payment_expires_at = COALESCE(
        GREATEST(o.payment_expires_at, NOW() + INTERVAL '30 minutes'),
        NOW() + INTERVAL '30 minutes'
    ),
    updated_at = NOW()
FROM seed_orders
LEFT JOIN bag_daily_stocks stock
    ON stock.bag_id = seed_orders.bag_id
   AND stock.date = seed_orders.target_pickup_date
WHERE o.id = seed_orders.id;
