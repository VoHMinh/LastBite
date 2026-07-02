-- Demo data for FE analytics wiring. Safe to keep in local/dev databases:
-- records use deterministic ids and demo_* identifiers.

INSERT INTO users (
    id, email, username, password_hash, full_name, phone, account_type, status,
    auth_provider, email_verified, phone_verified, must_change_password, created_at, updated_at
) VALUES
    ('11111111-1111-1111-1111-111111111111', 'demo.merchant@lastbite.local', 'demo.merchant',
     '$2a$12$1VjNTXbjShKkTwkXpNa69e1qGI/LvP/Jbm.CTUDkJlBNnb/iFZz2.', 'Demo Merchant Owner',
     '0900000001', 'PLATFORM', 'ACTIVE', 'LOCAL', TRUE, TRUE, FALSE, NOW() - INTERVAL '45 days', NOW()),
    ('11111111-1111-1111-1111-111111111112', NULL, 'demo.staff',
     '$2a$12$1VjNTXbjShKkTwkXpNa69e1qGI/LvP/Jbm.CTUDkJlBNnb/iFZz2.', 'Demo Store Staff',
     '0900000002', 'STORE_MEMBER', 'ACTIVE', 'LOCAL', TRUE, TRUE, FALSE, NOW() - INTERVAL '30 days', NOW()),
    ('11111111-1111-1111-1111-111111111113', 'demo.customer@lastbite.local', 'demo.customer',
     '$2a$12$1VjNTXbjShKkTwkXpNa69e1qGI/LvP/Jbm.CTUDkJlBNnb/iFZz2.', 'Demo Customer',
     '0900000003', 'PLATFORM', 'ACTIVE', 'LOCAL', TRUE, TRUE, FALSE, NOW() - INTERVAL '20 days', NOW())
ON CONFLICT (id) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    status = EXCLUDED.status,
    email_verified = EXCLUDED.email_verified,
    phone_verified = EXCLUDED.phone_verified,
    must_change_password = EXCLUDED.must_change_password,
    updated_at = NOW();

INSERT INTO user_roles (user_id, role_id)
SELECT seed.user_id, r.id
FROM (
    VALUES
        ('11111111-1111-1111-1111-111111111111'::uuid, 'MERCHANT_OWNER'),
        ('11111111-1111-1111-1111-111111111112'::uuid, 'STAFF'),
        ('11111111-1111-1111-1111-111111111113'::uuid, 'CUSTOMER')
) AS seed(user_id, role_code)
JOIN roles r ON r.code = seed.role_code
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO merchant_business_profiles (
    id, owner_user_id, legal_type, legal_name, representative_full_name,
    representative_phone, representative_email, business_address, review_status,
    approved_at, created_at, updated_at
) VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'HOUSEHOLD_BUSINESS',
    'Demo Bakery Rescue',
    'Demo Merchant Owner',
    '0900000001',
    'demo.merchant@lastbite.local',
    '123 Pasteur, Ben Nghe, District 1, Ho Chi Minh City',
    'APPROVED',
    NOW() - INTERVAL '35 days',
    NOW() - INTERVAL '45 days',
    NOW()
)
ON CONFLICT (id) DO UPDATE SET
    review_status = EXCLUDED.review_status,
    approved_at = EXCLUDED.approved_at,
    updated_at = NOW();

INSERT INTO stores (
    id, created_by_user_id, business_profile_id, name, slug, description, category,
    phone, email, address, district, city, lat, lng, pickup_instructions,
    cover_image_url, logo_url, storefront_image_url, menu_image_url,
    status, verification_status, avg_rating, total_ratings, created_at, updated_at
) VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Demo Bakery Rescue',
    'demo-bakery-rescue',
    'Demo store with enough activity for merchant engagement analytics.',
    'BAKERY',
    '0900000001',
    'demo.bakery@lastbite.local',
    '123 Pasteur, Ben Nghe, District 1, Ho Chi Minh City',
    'District 1',
    'ho-chi-minh',
    10.7769,
    106.7009,
    'Show pickup code at the counter.',
    'https://images.unsplash.com/photo-1509440159596-0249088772ff',
    'https://images.unsplash.com/photo-1517433367423-c7e5b0f35086',
    'https://images.unsplash.com/photo-1483695028939-5bb13f8648b0',
    'https://images.unsplash.com/photo-1509440159596-0249088772ff',
    'ACTIVE',
    'VERIFIED',
    4.6,
    128,
    NOW() - INTERVAL '40 days',
    NOW()
)
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    verification_status = EXCLUDED.verification_status,
    avg_rating = EXCLUDED.avg_rating,
    total_ratings = EXCLUDED.total_ratings,
    updated_at = NOW();

INSERT INTO store_schedules (store_id, day_of_week, open_time, close_time, is_open)
SELECT '33333333-3333-3333-3333-333333333333'::uuid, day, '07:00'::time, '21:30'::time, TRUE
FROM generate_series(0, 6) AS day
ON CONFLICT (store_id, day_of_week) DO UPDATE SET
    open_time = EXCLUDED.open_time,
    close_time = EXCLUDED.close_time,
    is_open = EXCLUDED.is_open,
    updated_at = NOW();

INSERT INTO merchant_store_members (
    id, user_id, store_id, role_id, status, created_by_user_id, joined_at, created_at, updated_at
)
SELECT
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111112',
    '33333333-3333-3333-3333-333333333333',
    r.id,
    'ACTIVE',
    '11111111-1111-1111-1111-111111111111',
    NOW() - INTERVAL '30 days',
    NOW() - INTERVAL '30 days',
    NOW()
FROM roles r
WHERE r.code = 'STAFF'
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO surprise_bags (
    id, store_id, name, description, bag_type, diet_type, category, bag_size, photos,
    minimum_value, base_sale_price, dynamic_min_price, dynamic_max_price,
    dynamic_pricing_enabled, platform_fee, max_per_order, container_provided,
    carrier_bag_provided, packaging_note, pickup_start_time, pickup_end_time,
    available_days, weekly_stock_plan, status, created_at, updated_at
) VALUES
    ('44444444-4444-4444-4444-444444444441', '33333333-3333-3333-3333-333333333333',
     'Evening pastry rescue bag', 'Assorted croissants, buns and sweet pastries from today.',
     'BREAD', 'VEGETARIAN', 'BAKERY', 'STANDARD',
     ARRAY['https://images.unsplash.com/photo-1509440159596-0249088772ff']::TEXT[],
     120000, 39000, 35000, 45000, TRUE, 4000, 2, TRUE, TRUE,
     'Bring your own tote if possible.', '18:00', '20:00',
     ARRAY[0,1,2,3,4,5,6], ARRAY[12,12,10,10,14,16,14], 'ACTIVE',
     NOW() - INTERVAL '30 days', NOW()),
    ('44444444-4444-4444-4444-444444444442', '33333333-3333-3333-3333-333333333333',
     'Sandwich lunch rescue bag', 'Savory sandwiches and bakery lunch items.',
     'MEAL', 'MEAT', 'BAKERY', 'SMALL',
     ARRAY['https://images.unsplash.com/photo-1528735602780-2552fd46c7af']::TEXT[],
     90000, 29000, 25000, 35000, TRUE, 4000, 2, TRUE, TRUE,
     'May contain egg, dairy and meat.', '11:00', '13:00',
     ARRAY[1,2,3,4,5], ARRAY[0,8,8,8,8,10,0], 'ACTIVE',
     NOW() - INTERVAL '25 days', NOW()),
    ('44444444-4444-4444-4444-444444444443', '33333333-3333-3333-3333-333333333333',
     'Cake slice surprise box', 'Mixed cake slices packed near closing time.',
     'STANDARD', 'VEGETARIAN', 'BAKERY', 'MINI',
     ARRAY['https://images.unsplash.com/photo-1488477181946-6428a0291777']::TEXT[],
     70000, 19000, 17000, 23000, TRUE, 4000, 3, TRUE, TRUE,
     'Best eaten today.', '19:00', '21:00',
     ARRAY[0,3,4,5,6], ARRAY[6,0,0,8,8,10,10], 'ACTIVE',
     NOW() - INTERVAL '18 days', NOW())
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    photos = EXCLUDED.photos,
    weekly_stock_plan = EXCLUDED.weekly_stock_plan,
    updated_at = NOW();

INSERT INTO bag_daily_stocks (
    id, bag_id, store_id, date, quantity, reserved, sold, status, stock_source, created_at, updated_at
) VALUES
    ('66666666-6666-6666-6666-666666666661', '44444444-4444-4444-4444-444444444441', '33333333-3333-3333-3333-333333333333', CURRENT_DATE, 18, 1, 10, 'ACTIVE', 'MANUAL', NOW(), NOW()),
    ('66666666-6666-6666-6666-666666666662', '44444444-4444-4444-4444-444444444442', '33333333-3333-3333-3333-333333333333', CURRENT_DATE, 12, 0, 7, 'ACTIVE', 'MANUAL', NOW(), NOW()),
    ('66666666-6666-6666-6666-666666666663', '44444444-4444-4444-4444-444444444443', '33333333-3333-3333-3333-333333333333', CURRENT_DATE, 10, 0, 5, 'ACTIVE', 'MANUAL', NOW(), NOW())
ON CONFLICT (bag_id, date) DO UPDATE SET
    quantity = EXCLUDED.quantity,
    reserved = EXCLUDED.reserved,
    sold = EXCLUDED.sold,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO orders (
    order_number, user_id, store_id, bag_id, daily_stock_id, quantity, unit_price,
    platform_fee, subtotal, discount_amount, final_amount, status, pickup_code,
    pickup_date, pickup_start_time, pickup_end_time, reserved_until,
    payment_expires_at, paid_at, picked_up_at, refund_status, idempotency_key,
    created_at, updated_at
)
SELECT
    'LB-DEMO-' || LPAD(n::text, 3, '0'),
    '11111111-1111-1111-1111-111111111113'::uuid,
    '33333333-3333-3333-3333-333333333333'::uuid,
    CASE WHEN n % 3 = 1 THEN '44444444-4444-4444-4444-444444444441'::uuid
         WHEN n % 3 = 2 THEN '44444444-4444-4444-4444-444444444442'::uuid
         ELSE '44444444-4444-4444-4444-444444444443'::uuid END,
    CASE WHEN n % 3 = 1 THEN '66666666-6666-6666-6666-666666666661'::uuid
         WHEN n % 3 = 2 THEN '66666666-6666-6666-6666-666666666662'::uuid
         ELSE '66666666-6666-6666-6666-666666666663'::uuid END,
    CASE WHEN n IN (4, 8, 12) THEN 2 ELSE 1 END,
    CASE WHEN n % 3 = 1 THEN 39000 WHEN n % 3 = 2 THEN 29000 ELSE 19000 END,
    4000,
    (CASE WHEN n IN (4, 8, 12) THEN 2 ELSE 1 END)
      * (CASE WHEN n % 3 = 1 THEN 39000 WHEN n % 3 = 2 THEN 29000 ELSE 19000 END),
    CASE WHEN n IN (3, 9) THEN 5000 ELSE 0 END,
    ((CASE WHEN n IN (4, 8, 12) THEN 2 ELSE 1 END)
      * (CASE WHEN n % 3 = 1 THEN 39000 WHEN n % 3 = 2 THEN 29000 ELSE 19000 END))
      - CASE WHEN n IN (3, 9) THEN 5000 ELSE 0 END,
    CASE WHEN n <= 8 THEN 'PICKED_UP' ELSE 'PAID' END,
    'D' || LPAD(n::text, 5, '0'),
    CURRENT_DATE,
    CASE WHEN n % 3 = 2 THEN '11:00'::time WHEN n % 3 = 1 THEN '18:00'::time ELSE '19:00'::time END,
    CASE WHEN n % 3 = 2 THEN '13:00'::time WHEN n % 3 = 1 THEN '20:00'::time ELSE '21:00'::time END,
    NOW() + INTERVAL '15 minutes',
    NOW() + INTERVAL '15 minutes',
    NOW() - ((13 - n) || ' hours')::interval,
    CASE WHEN n <= 8 THEN NOW() - ((12 - n) || ' hours')::interval ELSE NULL END,
    'NONE',
    'order:demo-engagement:' || n,
    NOW() - ((13 - n) || ' hours')::interval,
    NOW()
FROM generate_series(1, 12) AS n
ON CONFLICT (order_number) DO NOTHING;

INSERT INTO payments (
    order_id, user_id, provider, provider_order_code, provider_payment_link_id, amount,
    currency, status, checkout_url, expires_at, paid_at, idempotency_key,
    raw_provider_payload, created_at, updated_at
)
SELECT
    o.id,
    o.user_id,
    'FAKE',
    9202606000 + ROW_NUMBER() OVER (ORDER BY o.order_number),
    'demo-link-' || o.order_number,
    o.final_amount,
    'VND',
    'SUCCEEDED',
    'https://pay.demo.lastbite.local/' || o.order_number,
    o.payment_expires_at,
    o.paid_at,
    'payment:' || o.id,
    '{"seed":true}',
    o.created_at,
    NOW()
FROM orders o
WHERE o.order_number LIKE 'LB-DEMO-%'
ON CONFLICT (order_id) DO NOTHING;

DELETE FROM store_engagement_events
WHERE store_id = '33333333-3333-3333-3333-333333333333'
  AND source LIKE 'demo_seed_%';

INSERT INTO store_engagement_events (
    store_id, bag_id, user_id, event_type, source, session_id, occurred_at, created_at, updated_at
)
SELECT
    '33333333-3333-3333-3333-333333333333'::uuid,
    CASE WHEN n % 3 = 1 THEN '44444444-4444-4444-4444-444444444441'::uuid
         WHEN n % 3 = 2 THEN '44444444-4444-4444-4444-444444444442'::uuid
         ELSE '44444444-4444-4444-4444-444444444443'::uuid END,
    CASE WHEN n % 5 = 0 THEN '11111111-1111-1111-1111-111111111113'::uuid ELSE NULL END,
    CASE WHEN n % 4 = 0 THEN 'BAG_CARD_CLICK'
         WHEN n % 4 = 1 THEN 'BAG_VIEW'
         WHEN n % 4 = 2 THEN 'STORE_CARD_CLICK'
         ELSE 'STORE_VIEW' END,
    CASE WHEN n % 3 = 0 THEN 'demo_seed_home'
         WHEN n % 3 = 1 THEN 'demo_seed_customer_discovery'
         ELSE 'demo_seed_store_detail' END,
    'demo-session-' || (n % 18),
    NOW() - ((n % 72) || ' hours')::interval,
    NOW(),
    NOW()
FROM generate_series(1, 180) AS n;
