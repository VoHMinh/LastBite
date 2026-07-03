-- Keep the large play-test dataset from flooding startup schedulers.
-- Seed devices are not real FCM tokens, and active paid/ready seed orders
-- should stay testable instead of being immediately expired by pickup jobs.

UPDATE notification_devices
SET
    is_active = FALSE,
    updated_at = NOW()
WHERE device_token LIKE 'seed-device-token-%';

UPDATE orders
SET
    pickup_date = CURRENT_DATE + 1,
    reserved_until = GREATEST(reserved_until, NOW() + INTERVAL '30 minutes'),
    payment_expires_at = COALESCE(
        GREATEST(payment_expires_at, NOW() + INTERVAL '30 minutes'),
        NOW() + INTERVAL '30 minutes'
    ),
    updated_at = NOW()
WHERE (order_number LIKE 'LB-PLAY-%' OR order_number LIKE 'LB-DEMO-%')
  AND status IN ('PAID', 'READY_FOR_PICKUP')
  AND pickup_date <= CURRENT_DATE
  AND pickup_end_time <= CURRENT_TIME;
