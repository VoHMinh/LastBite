-- Full local/play-test seed data. Records are deterministic and use lb-play
-- identifiers so the dataset is easy to recognize in dev databases.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION lb_seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        SUBSTR(MD5(seed), 1, 8) || '-' ||
        SUBSTR(MD5(seed), 9, 4) || '-' ||
        SUBSTR(MD5(seed), 13, 4) || '-' ||
        SUBSTR(MD5(seed), 17, 4) || '-' ||
        SUBSTR(MD5(seed), 21, 12)
    )::UUID;
$$;

WITH seed_users(id, email, username, full_name, phone, account_type, role_code) AS (
    VALUES
        ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID, 'dev.customer@lastbite.test', 'dev_customer', 'Dev Customer', '0988000001', 'PLATFORM', 'CUSTOMER'),
        ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID, 'dev.merchant@lastbite.test', 'dev_merchant', 'Dev Merchant Owner', '0988000002', 'PLATFORM', 'MERCHANT_OWNER'),
        ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID, 'dev.store@lastbite.test', 'dev_store', 'Dev Store Manager', '0988000003', 'STORE_MEMBER', 'MANAGER')
    UNION ALL
    SELECT
        lb_seed_uuid('lb-play-user-' || n),
        'playtester' || LPAD(n::TEXT, 2, '0') || '@lastbite.test',
        'playtester' || LPAD(n::TEXT, 2, '0'),
        'Play Tester ' || LPAD(n::TEXT, 2, '0'),
        '09880001' || LPAD(n::TEXT, 2, '0'),
        'PLATFORM',
        'CUSTOMER'
    FROM generate_series(1, 20) AS tester(n)
)
INSERT INTO users (
    id, email, username, password_hash, full_name, phone, avatar_url, account_type,
    status, auth_provider, email_verified, phone_verified, must_change_password,
    created_at, updated_at
)
SELECT
    id,
    email,
    username,
    '$2a$12$doOEdK9Ciw5N.HGZClxbFumbQfZVHpewZRRZZUF3bpWTNgmC8bFmu',
    full_name,
    phone,
    'https://api.dicebear.com/8.x/initials/svg?seed=' || username,
    account_type,
    'ACTIVE',
    'LOCAL',
    TRUE,
    TRUE,
    FALSE,
    NOW() - INTERVAL '21 days',
    NOW()
FROM seed_users
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    username = EXCLUDED.username,
    password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    phone = EXCLUDED.phone,
    avatar_url = EXCLUDED.avatar_url,
    account_type = EXCLUDED.account_type,
    status = EXCLUDED.status,
    email_verified = TRUE,
    phone_verified = TRUE,
    must_change_password = FALSE,
    updated_at = NOW();

WITH seed_user_roles(user_id, role_code) AS (
    VALUES
        ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID, 'CUSTOMER'),
        ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID, 'MERCHANT_OWNER'),
        ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID, 'MANAGER')
    UNION ALL
    SELECT lb_seed_uuid('lb-play-user-' || n), 'CUSTOMER'
    FROM generate_series(1, 20) AS tester(n)
)
INSERT INTO user_roles (user_id, role_id, created_at)
SELECT seed.user_id, roles.id, NOW()
FROM seed_user_roles seed
JOIN roles ON roles.code = seed.role_code
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO merchant_business_profiles (
    id, owner_user_id, legal_type, legal_name, representative_full_name,
    representative_phone, representative_email, identity_document_type,
    identity_document_number, tax_code, registration_number, business_address,
    review_status, approved_at, created_at, updated_at
) VALUES (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001'::UUID,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
    'HOUSEHOLD_BUSINESS',
    'LastBite Di An Demo Group',
    'Dev Merchant Owner',
    '0988000002',
    'dev.merchant@lastbite.test',
    'CCCD',
    '079200000001',
    '3700000001',
    'DKKD-DIAN-0001',
    'Di An, Binh Duong, Viet Nam',
    'APPROVED',
    NOW() - INTERVAL '20 days',
    NOW() - INTERVAL '30 days',
    NOW()
)
ON CONFLICT (owner_user_id) DO UPDATE SET
    legal_name = EXCLUDED.legal_name,
    representative_full_name = EXCLUDED.representative_full_name,
    representative_phone = EXCLUDED.representative_phone,
    representative_email = EXCLUDED.representative_email,
    business_address = EXCLUDED.business_address,
    review_status = EXCLUDED.review_status,
    approved_at = EXCLUDED.approved_at,
    updated_at = NOW();

INSERT INTO merchant_business_profile_versions (
    id, business_profile_id, version_number, snapshot_json, review_status,
    submitted_at, reviewed_at, created_at, updated_at
) VALUES (
    lb_seed_uuid('lb-play-business-profile-version-1'),
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001'::UUID,
    1,
    '{"seed":true,"legalName":"LastBite Di An Demo Group","city":"di-an"}',
    'APPROVED',
    NOW() - INTERVAL '22 days',
    NOW() - INTERVAL '20 days',
    NOW() - INTERVAL '22 days',
    NOW()
)
ON CONFLICT (business_profile_id, version_number) DO UPDATE SET
    snapshot_json = EXCLUDED.snapshot_json,
    review_status = EXCLUDED.review_status,
    reviewed_at = EXCLUDED.reviewed_at,
    updated_at = NOW();

INSERT INTO merchant_bank_accounts (
    id, business_profile_id, bank_code, bank_name, account_holder_name,
    account_number_encrypted, account_number_last4, is_default,
    verification_status, created_at, updated_at
) VALUES (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0002'::UUID,
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001'::UUID,
    'VCB',
    'Vietcombank',
    'LASTBITE DI AN DEMO GROUP',
    'seed-encrypted-bank-account-0001',
    '0001',
    TRUE,
    'APPROVED',
    NOW() - INTERVAL '20 days',
    NOW()
)
ON CONFLICT (id) DO UPDATE SET
    bank_code = EXCLUDED.bank_code,
    bank_name = EXCLUDED.bank_name,
    account_holder_name = EXCLUDED.account_holder_name,
    account_number_encrypted = EXCLUDED.account_number_encrypted,
    account_number_last4 = EXCLUDED.account_number_last4,
    is_default = TRUE,
    verification_status = EXCLUDED.verification_status,
    updated_at = NOW();

WITH store_seed AS (
    SELECT
        n,
        lb_seed_uuid('lb-play-store-' || n) AS store_id,
        (ARRAY['BAKERY', 'CAFE', 'RESTAURANT', 'GROCERY', 'CONVENIENCE'])[((n - 1) % 5) + 1] AS category,
        (ARRAY['Phuong Di An', 'Phuong Dong Hoa', 'Phuong Tan Dong Hiep', 'Phuong Binh An', 'Phuong An Binh', 'Phuong Binh Thang'])[((n - 1) % 6) + 1] AS ward,
        (ARRAY['QL1K', 'DT743', 'Nguyen An Ninh', 'Tran Hung Dao', 'Ly Thuong Kiet', 'Nguyen Tri Phuong', 'My Phuoc Tan Van', 'Vo Thi Sau'])[((n - 1) % 8) + 1] AS street
    FROM generate_series(1, 1000) AS seed(n)
)
INSERT INTO stores (
    id, created_by_user_id, business_profile_id, name, slug, description, category,
    phone, email, address, district, city, lat, lng, pickup_instructions,
    cover_image_url, logo_url, storefront_image_url, menu_image_url,
    status, verification_status, avg_rating, total_ratings, created_at, updated_at
)
SELECT
    store_id,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001'::UUID,
    CASE WHEN n = 1 THEN 'LastBite Di An Dev Store'
         ELSE 'LastBite Di An ' || INITCAP(LOWER(category)) || ' ' || LPAD(n::TEXT, 4, '0') END,
    'lb-di-an-store-' || LPAD(n::TEXT, 4, '0'),
    'Seed store in Di An, Binh Duong for mobile, merchant, order, payment and discovery testing.',
    category,
    '0274' || LPAD(n::TEXT, 6, '0'),
    'store' || LPAD(n::TEXT, 4, '0') || '@lastbite.test',
    (20 + (n % 180)) || ' ' || street || ', ' || ward || ', Di An, Binh Duong, Viet Nam',
    'Di An',
    'di-an',
    10.885000 + (((n - 1) % 50) * 0.000900) + (((n - 1) / 50) * 0.000120),
    106.735000 + (((n - 1) % 40) * 0.001000) + (((n - 1) / 40) * 0.000110),
    'Show pickup code or QR to staff at the counter.',
    CASE category
        WHEN 'BAKERY' THEN 'https://images.unsplash.com/photo-1509440159596-0249088772ff'
        WHEN 'CAFE' THEN 'https://images.unsplash.com/photo-1509042239860-f550ce710b93'
        WHEN 'RESTAURANT' THEN 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4'
        WHEN 'GROCERY' THEN 'https://images.unsplash.com/photo-1542838132-92c53300491e'
        ELSE 'https://images.unsplash.com/photo-1580913428706-c311e67898b3'
    END,
    CASE category
        WHEN 'BAKERY' THEN 'https://images.unsplash.com/photo-1517433367423-c7e5b0f35086'
        WHEN 'CAFE' THEN 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085'
        WHEN 'RESTAURANT' THEN 'https://images.unsplash.com/photo-1552566626-52f8b828add9'
        WHEN 'GROCERY' THEN 'https://images.unsplash.com/photo-1601599963565-b7ba29c8d51b'
        ELSE 'https://images.unsplash.com/photo-1604719312566-8912e9227c6a'
    END,
    CASE category
        WHEN 'BAKERY' THEN 'https://images.unsplash.com/photo-1483695028939-5bb13f8648b0'
        WHEN 'CAFE' THEN 'https://images.unsplash.com/photo-1442512595331-e89e73853f31'
        WHEN 'RESTAURANT' THEN 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0'
        WHEN 'GROCERY' THEN 'https://images.unsplash.com/photo-1578916171728-46686eac8d58'
        ELSE 'https://images.unsplash.com/photo-1578916171728-46686eac8d58'
    END,
    CASE category
        WHEN 'BAKERY' THEN 'https://images.unsplash.com/photo-1509440159596-0249088772ff'
        WHEN 'CAFE' THEN 'https://images.unsplash.com/photo-1509042239860-f550ce710b93'
        WHEN 'RESTAURANT' THEN 'https://images.unsplash.com/photo-1543353071-873f17a7a088'
        WHEN 'GROCERY' THEN 'https://images.unsplash.com/photo-1542838132-92c53300491e'
        ELSE 'https://images.unsplash.com/photo-1580913428706-c311e67898b3'
    END,
    'ACTIVE',
    'VERIFIED',
    ROUND((4.10 + ((n % 9) * 0.10))::NUMERIC, 2),
    25 + (n % 420),
    NOW() - ((n % 120) || ' days')::INTERVAL,
    NOW()
FROM store_seed
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    phone = EXCLUDED.phone,
    email = EXCLUDED.email,
    address = EXCLUDED.address,
    district = EXCLUDED.district,
    city = EXCLUDED.city,
    lat = EXCLUDED.lat,
    lng = EXCLUDED.lng,
    pickup_instructions = EXCLUDED.pickup_instructions,
    cover_image_url = EXCLUDED.cover_image_url,
    logo_url = EXCLUDED.logo_url,
    storefront_image_url = EXCLUDED.storefront_image_url,
    menu_image_url = EXCLUDED.menu_image_url,
    status = EXCLUDED.status,
    verification_status = EXCLUDED.verification_status,
    avg_rating = EXCLUDED.avg_rating,
    total_ratings = EXCLUDED.total_ratings,
    updated_at = NOW();

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-play-store-' || n) AS store_id
    FROM generate_series(1, 1000) AS seed(n)
)
INSERT INTO store_schedules (
    id, store_id, day_of_week, open_time, close_time, is_open, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-store-schedule-' || n || '-' || day_of_week),
    store_id,
    day_of_week,
    CASE WHEN n % 5 IN (0, 1) THEN '06:30'::TIME ELSE '08:00'::TIME END,
    CASE WHEN n % 4 = 0 THEN '22:30'::TIME ELSE '21:30'::TIME END,
    TRUE,
    NOW() - INTERVAL '20 days',
    NOW()
FROM store_seed
CROSS JOIN generate_series(0, 6) AS days(day_of_week)
ON CONFLICT (store_id, day_of_week) DO UPDATE SET
    open_time = EXCLUDED.open_time,
    close_time = EXCLUDED.close_time,
    is_open = TRUE,
    updated_at = NOW();

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-play-store-' || n) AS store_id
    FROM generate_series(1, 1000) AS seed(n)
)
INSERT INTO store_reliability_stats (
    store_id, total_bags_listed, total_bags_sold, total_bags_fulfilled,
    total_bags_no_show, fulfillment_rate, warning_count, is_under_review,
    merchant_cancelled_count, store_fault_refund_count, last_recalculated_at,
    created_at, updated_at
)
SELECT
    store_id,
    120 + (n % 400),
    60 + (n % 300),
    58 + (n % 280),
    n % 5,
    ROUND((0.92 + ((n % 7) * 0.01))::NUMERIC, 2),
    n % 3,
    FALSE,
    n % 4,
    n % 3,
    NOW(),
    NOW() - INTERVAL '20 days',
    NOW()
FROM store_seed
ON CONFLICT (store_id) DO UPDATE SET
    total_bags_listed = EXCLUDED.total_bags_listed,
    total_bags_sold = EXCLUDED.total_bags_sold,
    total_bags_fulfilled = EXCLUDED.total_bags_fulfilled,
    total_bags_no_show = EXCLUDED.total_bags_no_show,
    fulfillment_rate = EXCLUDED.fulfillment_rate,
    warning_count = EXCLUDED.warning_count,
    is_under_review = FALSE,
    merchant_cancelled_count = EXCLUDED.merchant_cancelled_count,
    store_fault_refund_count = EXCLUDED.store_fault_refund_count,
    last_recalculated_at = NOW(),
    updated_at = NOW();

INSERT INTO merchant_store_members (
    id, user_id, store_id, role_id, status, created_by_user_id, joined_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-dev-store-manager-membership'),
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID,
    lb_seed_uuid('lb-play-store-1'),
    roles.id,
    'ACTIVE',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
    NOW() - INTERVAL '19 days',
    NOW() - INTERVAL '19 days',
    NOW()
FROM roles
WHERE roles.code = 'MANAGER'
ON CONFLICT (id) DO UPDATE SET
    store_id = EXCLUDED.store_id,
    role_id = EXCLUDED.role_id,
    status = 'ACTIVE',
    updated_at = NOW();

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-play-store-' || n) AS store_id
    FROM generate_series(1, 25) AS seed(n)
)
INSERT INTO store_versions (
    id, store_id, version_number, snapshot_json, review_status,
    submitted_at, reviewed_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-store-version-' || n || '-1'),
    store_id,
    1,
    jsonb_build_object('seed', TRUE, 'storeNo', n, 'city', 'di-an')::TEXT,
    'APPROVED',
    NOW() - INTERVAL '18 days',
    NOW() - INTERVAL '17 days',
    NOW() - INTERVAL '18 days',
    NOW()
FROM store_seed
ON CONFLICT (store_id, version_number) DO UPDATE SET
    snapshot_json = EXCLUDED.snapshot_json,
    review_status = EXCLUDED.review_status,
    reviewed_at = EXCLUDED.reviewed_at,
    updated_at = NOW();

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-play-store-' || n) AS store_id
    FROM generate_series(1, 25) AS seed(n)
)
INSERT INTO store_review_applications (
    id, store_id, business_profile_version_id, store_version_id, status,
    submitted_by, submitted_at, reviewed_at, decision_note, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-store-review-application-' || n),
    store_id,
    lb_seed_uuid('lb-play-business-profile-version-1'),
    lb_seed_uuid('lb-play-store-version-' || n || '-1'),
    'APPROVED',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
    NOW() - INTERVAL '18 days',
    NOW() - INTERVAL '17 days',
    'Seed approved for play-store testing.',
    NOW() - INTERVAL '18 days',
    NOW()
FROM store_seed
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    decision_note = EXCLUDED.decision_note,
    updated_at = NOW();

WITH store_seed AS (
    SELECT
        n AS store_no,
        lb_seed_uuid('lb-play-store-' || n) AS store_id,
        (ARRAY['BAKERY', 'CAFE', 'RESTAURANT', 'GROCERY', 'CONVENIENCE'])[((n - 1) % 5) + 1] AS category
    FROM generate_series(1, 1000) AS seed(n)
),
bag_seed AS (
    SELECT
        store_no,
        store_id,
        bag_slot,
        lb_seed_uuid('lb-play-bag-' || store_no || '-' || bag_slot) AS bag_id,
        category,
        CASE
            WHEN category = 'BAKERY' THEN 'BREAD'
            WHEN category = 'RESTAURANT' THEN 'MEAL'
            WHEN category = 'GROCERY' THEN 'GROCERY'
            WHEN category = 'CAFE' THEN 'MIXED'
            ELSE 'MIXED'
        END AS bag_type,
        CASE
            WHEN bag_slot = 1 AND category IN ('BAKERY', 'CAFE') THEN 'VEGETARIAN'
            WHEN bag_slot = 2 AND store_no % 6 = 0 THEN 'VEGAN'
            ELSE 'MEAT'
        END AS diet_type,
        CASE WHEN bag_slot = 1 THEN 'STANDARD' ELSE 'SMALL' END AS bag_size
    FROM store_seed
    CROSS JOIN generate_series(1, 2) AS slot(bag_slot)
)
INSERT INTO surprise_bags (
    id, store_id, name, description, bag_type, diet_type, category, bag_size, photos,
    minimum_value, base_sale_price, dynamic_min_price, dynamic_max_price,
    dynamic_pricing_enabled, platform_fee, max_per_order, container_provided,
    carrier_bag_provided, packaging_note, pickup_start_time, pickup_end_time,
    available_days, weekly_stock_plan, status, version, created_at, updated_at
)
SELECT
    bag_seed.bag_id,
    bag_seed.store_id,
    CASE WHEN bag_seed.bag_slot = 1
         THEN INITCAP(LOWER(bag_seed.category)) || ' lunch rescue bag ' || LPAD(bag_seed.store_no::TEXT, 4, '0')
         ELSE INITCAP(LOWER(bag_seed.category)) || ' late day surprise bag ' || LPAD(bag_seed.store_no::TEXT, 4, '0') END,
    'Seed surprise bag with stock, payment, pickup and review coverage for end-to-end testing.',
    bag_seed.bag_type,
    bag_seed.diet_type,
    bag_seed.category,
    bag_seed.bag_size,
    CASE bag_seed.category
        WHEN 'BAKERY' THEN ARRAY['https://images.unsplash.com/photo-1509440159596-0249088772ff', 'https://images.unsplash.com/photo-1488477181946-6428a0291777']::TEXT[]
        WHEN 'CAFE' THEN ARRAY['https://images.unsplash.com/photo-1509042239860-f550ce710b93', 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085']::TEXT[]
        WHEN 'RESTAURANT' THEN ARRAY['https://images.unsplash.com/photo-1543353071-873f17a7a088', 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4']::TEXT[]
        WHEN 'GROCERY' THEN ARRAY['https://images.unsplash.com/photo-1542838132-92c53300491e', 'https://images.unsplash.com/photo-1578916171728-46686eac8d58']::TEXT[]
        ELSE ARRAY['https://images.unsplash.com/photo-1580913428706-c311e67898b3', 'https://images.unsplash.com/photo-1604719312566-8912e9227c6a']::TEXT[]
    END,
    tiers.minimum_value,
    tiers.base_sale_price,
    tiers.dynamic_min_price,
    tiers.dynamic_max_price,
    TRUE,
    tiers.platform_fee,
    CASE WHEN bag_seed.bag_slot = 1 THEN 2 ELSE 3 END,
    TRUE,
    TRUE,
    CASE WHEN bag_seed.diet_type = 'VEGAN' THEN 'Vegan-friendly seed bag; confirm allergens with staff.'
         WHEN bag_seed.diet_type = 'VEGETARIAN' THEN 'Vegetarian seed bag; may contain egg or dairy.'
         ELSE 'May contain meat, egg, dairy or seafood depending on daily surplus.' END,
    CASE
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'CAFE' THEN '08:00'::TIME
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'RESTAURANT' THEN '11:00'::TIME
        WHEN bag_seed.bag_slot = 1 THEN '10:30'::TIME
        ELSE '20:00'::TIME
    END,
    CASE
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'CAFE' THEN '10:30'::TIME
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'RESTAURANT' THEN '14:00'::TIME
        WHEN bag_seed.bag_slot = 1 THEN '13:00'::TIME
        ELSE '23:30'::TIME
    END,
    ARRAY[0,1,2,3,4,5,6],
    ARRAY[
        8 + (bag_seed.store_no % 8),
        10 + (bag_seed.store_no % 8),
        10 + (bag_seed.store_no % 9),
        12 + (bag_seed.store_no % 8),
        14 + (bag_seed.store_no % 9),
        16 + (bag_seed.store_no % 10),
        12 + (bag_seed.store_no % 8)
    ],
    'ACTIVE',
    1,
    NOW() - ((bag_seed.store_no % 90) || ' days')::INTERVAL,
    NOW()
FROM bag_seed
JOIN bag_price_tiers tiers ON tiers.category = bag_seed.category AND tiers.bag_size = bag_seed.bag_size
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    bag_type = EXCLUDED.bag_type,
    diet_type = EXCLUDED.diet_type,
    category = EXCLUDED.category,
    bag_size = EXCLUDED.bag_size,
    photos = EXCLUDED.photos,
    minimum_value = EXCLUDED.minimum_value,
    base_sale_price = EXCLUDED.base_sale_price,
    dynamic_min_price = EXCLUDED.dynamic_min_price,
    dynamic_max_price = EXCLUDED.dynamic_max_price,
    platform_fee = EXCLUDED.platform_fee,
    max_per_order = EXCLUDED.max_per_order,
    packaging_note = EXCLUDED.packaging_note,
    pickup_start_time = EXCLUDED.pickup_start_time,
    pickup_end_time = EXCLUDED.pickup_end_time,
    available_days = EXCLUDED.available_days,
    weekly_stock_plan = EXCLUDED.weekly_stock_plan,
    status = EXCLUDED.status,
    updated_at = NOW();

WITH bag_seed AS (
    SELECT
        store_no,
        bag_slot,
        lb_seed_uuid('lb-play-store-' || store_no) AS store_id,
        lb_seed_uuid('lb-play-bag-' || store_no || '-' || bag_slot) AS bag_id
    FROM generate_series(1, 1000) AS stores(store_no)
    CROSS JOIN generate_series(1, 2) AS slots(bag_slot)
),
stock_seed AS (
    SELECT
        store_no,
        bag_slot,
        store_id,
        bag_id,
        day_offset,
        CURRENT_DATE + day_offset AS stock_date,
        12 + ((store_no + bag_slot + day_offset + 60) % 18) AS quantity,
        CASE WHEN day_offset < 0 THEN 2 + ((store_no + bag_slot + day_offset + 100) % 8)
             WHEN day_offset = 0 THEN (store_no + bag_slot) % 4
             ELSE 0 END AS sold,
        CASE WHEN day_offset = 0 THEN (store_no + bag_slot) % 2 ELSE 0 END AS reserved
    FROM bag_seed
    CROSS JOIN generate_series(-30, 13) AS offsets(day_offset)
)
INSERT INTO bag_daily_stocks (
    id, bag_id, store_id, date, quantity, reserved, sold, status,
    version, stock_source, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-stock-' || store_no || '-' || bag_slot || '-' || stock_date::TEXT),
    bag_id,
    store_id,
    stock_date,
    quantity,
    reserved,
    sold,
    CASE WHEN day_offset < 0 THEN 'EXPIRED' ELSE 'ACTIVE' END,
    1,
    'WEEKLY_DEFAULT',
    NOW() - INTERVAL '30 days',
    NOW()
FROM stock_seed
ON CONFLICT (bag_id, date) DO UPDATE SET
    quantity = EXCLUDED.quantity,
    reserved = EXCLUDED.reserved,
    sold = EXCLUDED.sold,
    status = EXCLUDED.status,
    stock_source = EXCLUDED.stock_source,
    updated_at = NOW();

WITH customer_seed AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
),
order_seed AS (
    SELECT
        n,
        customer_seed.user_id,
        ((n - 1) % 1000) + 1 AS store_no,
        CASE WHEN n % 2 = 0 THEN 2 ELSE 1 END AS bag_slot,
        CASE
            WHEN n % 12 IN (4, 5, 11) THEN 'PICKED_UP'
            WHEN n % 12 = 2 THEN 'PAID'
            WHEN n % 12 IN (3, 10) THEN 'READY_FOR_PICKUP'
            WHEN n % 12 = 6 THEN 'CANCELLED'
            WHEN n % 12 = 7 THEN 'EXPIRED'
            WHEN n % 12 = 8 THEN 'REFUNDED'
            WHEN n % 12 = 9 THEN 'PAID'
            ELSE 'PENDING_PAYMENT'
        END AS order_status,
        CASE
            WHEN n % 12 IN (4, 5, 6, 7, 8, 11) THEN -1 * ((n % 21) + 1)
            ELSE 0
        END AS day_offset
    FROM generate_series(1, 360) AS orders(n)
    JOIN customer_seed ON customer_seed.idx = ((n - 1) % 21)
),
order_joined AS (
    SELECT
        order_seed.*,
        lb_seed_uuid('lb-play-store-' || store_no) AS store_id,
        lb_seed_uuid('lb-play-bag-' || store_no || '-' || bag_slot) AS bag_id,
        CURRENT_DATE + day_offset AS pickup_date
    FROM order_seed
),
priced_orders AS (
    SELECT
        order_joined.*,
        lb_seed_uuid('lb-play-stock-' || store_no || '-' || bag_slot || '-' || pickup_date::TEXT) AS stock_id,
        bags.base_sale_price,
        bags.platform_fee,
        bags.pickup_start_time,
        bags.pickup_end_time,
        CASE WHEN n % 15 = 0 THEN 2 ELSE 1 END AS quantity,
        CASE WHEN n % 9 = 0 THEN 5000 ELSE 0 END AS discount_amount
    FROM order_joined
    JOIN surprise_bags bags ON bags.id = order_joined.bag_id
)
INSERT INTO orders (
    id, order_number, user_id, store_id, bag_id, daily_stock_id, quantity,
    unit_price, platform_fee, subtotal, discount_amount, final_amount, status,
    pickup_code, pickup_date, pickup_start_time, pickup_end_time, reserved_until,
    payment_expires_at, paid_at, cancelled_at, expired_at, picked_up_at,
    refund_status, pickup_qr_token_hash, pickup_qr_token_encrypted,
    pickup_code_hash, idempotency_key, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-order-' || n),
    'LB-PLAY-' || LPAD(n::TEXT, 6, '0'),
    user_id,
    store_id,
    bag_id,
    stock_id,
    quantity,
    base_sale_price,
    platform_fee,
    base_sale_price * quantity,
    discount_amount,
    (base_sale_price * quantity) - discount_amount,
    order_status,
    'P' || LPAD(n::TEXT, 6, '0'),
    pickup_date,
    pickup_start_time,
    pickup_end_time,
    NOW() + INTERVAL '20 minutes',
    NOW() + INTERVAL '20 minutes',
    CASE WHEN order_status IN ('PAID', 'READY_FOR_PICKUP', 'PICKED_UP', 'REFUNDED') THEN NOW() - ((n % 96) || ' hours')::INTERVAL ELSE NULL END,
    CASE WHEN order_status = 'CANCELLED' THEN NOW() - ((n % 72) || ' hours')::INTERVAL ELSE NULL END,
    CASE WHEN order_status = 'EXPIRED' THEN NOW() - ((n % 72) || ' hours')::INTERVAL ELSE NULL END,
    CASE WHEN order_status IN ('PICKED_UP', 'REFUNDED') THEN NOW() - ((n % 48) || ' hours')::INTERVAL ELSE NULL END,
    CASE WHEN order_status = 'REFUNDED' THEN 'REFUNDED' ELSE 'NONE' END,
    MD5('qr-token-' || n),
    'seed-encrypted-qr-token-' || n,
    MD5('pickup-code-' || n),
    'lb-play-order:' || n,
    NOW() - ((n % 30) || ' days')::INTERVAL,
    NOW()
FROM priced_orders
ON CONFLICT (order_number) DO UPDATE SET
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at,
    cancelled_at = EXCLUDED.cancelled_at,
    expired_at = EXCLUDED.expired_at,
    picked_up_at = EXCLUDED.picked_up_at,
    refund_status = EXCLUDED.refund_status,
    pickup_qr_token_hash = EXCLUDED.pickup_qr_token_hash,
    pickup_qr_token_encrypted = EXCLUDED.pickup_qr_token_encrypted,
    pickup_code_hash = EXCLUDED.pickup_code_hash,
    updated_at = NOW();

INSERT INTO payments (
    id, order_id, user_id, provider, provider_order_code, provider_payment_link_id,
    amount, currency, status, checkout_url, qr_code, expires_at, paid_at,
    cancelled_at, failure_reason, idempotency_key, raw_provider_payload,
    created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-payment-' || o.order_number),
    o.id,
    o.user_id,
    'FAKE',
    9302607000 + ROW_NUMBER() OVER (ORDER BY o.order_number),
    'lb-play-link-' || o.order_number,
    o.final_amount,
    'VND',
    CASE
        WHEN o.status = 'PENDING_PAYMENT' THEN 'PENDING'
        WHEN o.status = 'CANCELLED' THEN 'CANCELLED'
        WHEN o.status = 'EXPIRED' THEN 'EXPIRED'
        WHEN o.status = 'REFUNDED' THEN 'REFUNDED'
        ELSE 'SUCCEEDED'
    END,
    'https://pay.seed.lastbite.test/' || o.order_number,
    'seed-qr-' || o.order_number,
    COALESCE(o.payment_expires_at, NOW() + INTERVAL '20 minutes'),
    o.paid_at,
    o.cancelled_at,
    CASE WHEN o.status = 'EXPIRED' THEN 'Seed payment expired' ELSE NULL END,
    'lb-play-payment:' || o.id,
    jsonb_build_object('seed', TRUE, 'orderNumber', o.order_number)::TEXT,
    o.created_at,
    NOW()
FROM orders o
WHERE o.order_number LIKE 'LB-PLAY-%'
ON CONFLICT (order_id) DO UPDATE SET
    amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at,
    cancelled_at = EXCLUDED.cancelled_at,
    failure_reason = EXCLUDED.failure_reason,
    updated_at = NOW();

INSERT INTO payment_transactions (
    id, payment_id, provider, provider_transaction_id, amount, currency,
    status, provider_code, provider_description, paid_at, raw_payload,
    created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-payment-transaction-' || p.id),
    p.id,
    'FAKE',
    'lb-play-txn-' || p.provider_order_code,
    p.amount,
    'VND',
    CASE WHEN p.status IN ('SUCCEEDED', 'REFUNDED', 'PARTIALLY_REFUNDED') THEN 'SUCCEEDED'
         WHEN p.status = 'CANCELLED' THEN 'CANCELLED'
         WHEN p.status = 'PENDING' THEN 'PENDING'
         ELSE 'FAILED' END,
    'SEED',
    'Seed payment transaction',
    p.paid_at,
    jsonb_build_object('seed', TRUE, 'providerOrderCode', p.provider_order_code)::TEXT,
    p.created_at,
    NOW()
FROM payments p
JOIN orders o ON o.id = p.order_id
WHERE o.order_number LIKE 'LB-PLAY-%'
ON CONFLICT DO NOTHING;

INSERT INTO payment_gateway_requests (
    id, payment_id, provider, request_type, idempotency_key, status,
    request_payload, response_payload, provider_reference, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-payment-gateway-request-' || p.id),
    p.id,
    'FAKE',
    'CREATE_PAYMENT_LINK',
    'lb-play-gateway:' || p.id,
    CASE WHEN p.status = 'PENDING' THEN 'PENDING' ELSE 'SUCCEEDED' END,
    jsonb_build_object('seed', TRUE, 'amount', p.amount)::TEXT,
    jsonb_build_object('checkoutUrl', p.checkout_url)::TEXT,
    p.provider_payment_link_id,
    p.created_at,
    NOW()
FROM payments p
JOIN orders o ON o.id = p.order_id
WHERE o.order_number LIKE 'LB-PLAY-%'
ON CONFLICT (provider, idempotency_key) DO UPDATE SET
    status = EXCLUDED.status,
    response_payload = EXCLUDED.response_payload,
    updated_at = NOW();

INSERT INTO payment_webhooks (
    id, provider, event_key, provider_order_code, payment_id, payload,
    valid_signature, processed, processed_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-payment-webhook-' || p.id),
    'FAKE',
    'lb-play-payment-webhook-' || p.provider_order_code,
    p.provider_order_code,
    p.id,
    jsonb_build_object('seed', TRUE, 'status', p.status)::TEXT,
    TRUE,
    TRUE,
    NOW(),
    p.created_at,
    NOW()
FROM payments p
JOIN orders o ON o.id = p.order_id
WHERE o.order_number LIKE 'LB-PLAY-%'
  AND p.status IN ('SUCCEEDED', 'REFUNDED')
ON CONFLICT (event_key) DO UPDATE SET
    payment_id = EXCLUDED.payment_id,
    payload = EXCLUDED.payload,
    processed = TRUE,
    processed_at = NOW(),
    updated_at = NOW();

INSERT INTO order_status_history (
    id, order_id, from_status, to_status, actor_user_id, actor_type,
    reason, metadata, created_at
)
SELECT
    lb_seed_uuid('lb-play-order-history-create-' || o.order_number),
    o.id,
    NULL,
    'PENDING_PAYMENT',
    o.user_id,
    'CUSTOMER',
    'Seed order created',
    jsonb_build_object('seed', TRUE)::TEXT,
    o.created_at
FROM orders o
WHERE o.order_number LIKE 'LB-PLAY-%'
ON CONFLICT (id) DO NOTHING;

INSERT INTO order_status_history (
    id, order_id, from_status, to_status, actor_user_id, actor_type,
    reason, metadata, created_at
)
SELECT
    lb_seed_uuid('lb-play-order-history-final-' || o.order_number),
    o.id,
    'PENDING_PAYMENT',
    o.status,
    CASE WHEN o.status IN ('READY_FOR_PICKUP', 'PICKED_UP') THEN 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID ELSE NULL END,
    CASE WHEN o.status IN ('READY_FOR_PICKUP', 'PICKED_UP') THEN 'MERCHANT' ELSE 'SYSTEM' END,
    'Seed order moved to ' || o.status,
    jsonb_build_object('seed', TRUE)::TEXT,
    COALESCE(o.paid_at, o.cancelled_at, o.expired_at, o.picked_up_at, o.created_at + INTERVAL '5 minutes')
FROM orders o
WHERE o.order_number LIKE 'LB-PLAY-%'
  AND o.status <> 'PENDING_PAYMENT'
ON CONFLICT (id) DO NOTHING;

INSERT INTO pickup_events (
    id, order_id, store_id, actor_user_id, event_type, channel,
    notes, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-pickup-event-' || o.order_number),
    o.id,
    o.store_id,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID,
    'CONFIRMED',
    CASE WHEN CAST(RIGHT(o.order_number, 1) AS INTEGER) % 2 = 0 THEN 'QR_SCAN' ELSE 'MANUAL_CODE' END,
    'Seed pickup confirmation.',
    COALESCE(o.picked_up_at, NOW()),
    NOW()
FROM orders o
WHERE o.order_number LIKE 'LB-PLAY-%'
  AND o.status IN ('PICKED_UP', 'REFUNDED')
ON CONFLICT (id) DO UPDATE SET
    event_type = EXCLUDED.event_type,
    channel = EXCLUDED.channel,
    notes = EXCLUDED.notes,
    updated_at = NOW();

INSERT INTO refund_requests (
    id, order_id, payment_id, requested_by_user_id, reason, status,
    requested_amount, approved_amount, description, decision_note,
    reviewed_at, auto_created, refund_bank_code, refund_bank_name,
    refund_account_holder_name, refund_account_number_encrypted,
    refund_account_number_last4, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-refund-request-' || o.order_number),
    o.id,
    p.id,
    o.user_id,
    CASE WHEN CAST(RIGHT(o.order_number, 2) AS INTEGER) % 2 = 0 THEN 'QUALITY_ISSUE' ELSE 'STORE_NO_STOCK' END,
    'REFUNDED',
    o.final_amount,
    o.final_amount,
    'Seed refunded order for refund flow testing.',
    'Seed refund approved.',
    NOW() - INTERVAL '12 hours',
    FALSE,
    'VCB',
    'Vietcombank',
    'SEED CUSTOMER',
    'seed-encrypted-refund-account-' || o.order_number,
    RIGHT(o.order_number, 4),
    o.created_at + INTERVAL '1 hour',
    NOW()
FROM orders o
JOIN payments p ON p.order_id = o.id
WHERE o.order_number LIKE 'LB-PLAY-%'
  AND o.status = 'REFUNDED'
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    approved_amount = EXCLUDED.approved_amount,
    decision_note = EXCLUDED.decision_note,
    updated_at = NOW();

INSERT INTO refund_transactions (
    id, refund_request_id, provider, provider_reference, amount, status,
    method, raw_payload, processed_at, failure_reason, idempotency_key,
    attempt_count, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-refund-transaction-' || rr.id),
    rr.id,
    'PAYOS',
    'lb-play-refund-' || rr.id,
    rr.approved_amount,
    'SUCCEEDED',
    'MANUAL_BANK_TRANSFER',
    jsonb_build_object('seed', TRUE)::TEXT,
    NOW() - INTERVAL '10 hours',
    NULL,
    'lb-play-refund-transaction:' || rr.id,
    1,
    rr.created_at + INTERVAL '15 minutes',
    NOW()
FROM refund_requests rr
JOIN orders o ON o.id = rr.order_id
WHERE o.order_number LIKE 'LB-PLAY-%'
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    processed_at = EXCLUDED.processed_at,
    updated_at = NOW();

INSERT INTO reviews (
    id, order_id, user_id, store_id, bag_id, overall_rating,
    collection_rating, quality_rating, variety_rating, quantity_rating,
    comment, visible, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-review-' || o.order_number),
    o.id,
    o.user_id,
    o.store_id,
    o.bag_id,
    4 + (CAST(RIGHT(o.order_number, 1) AS INTEGER) % 2),
    4,
    4 + (CAST(RIGHT(o.order_number, 1) AS INTEGER) % 2),
    4,
    4 + (CAST(RIGHT(o.order_number, 1) AS INTEGER) % 2),
    CASE WHEN CAST(RIGHT(o.order_number, 1) AS INTEGER) % 2 = 0
         THEN 'Good value seed review for pickup testing.'
         ELSE 'Fresh enough and easy pickup in Di An.' END,
    TRUE,
    COALESCE(o.picked_up_at, NOW()) + INTERVAL '30 minutes',
    NOW()
FROM orders o
WHERE o.order_number LIKE 'LB-PLAY-%'
  AND o.status = 'PICKED_UP'
ON CONFLICT (order_id) DO UPDATE SET
    overall_rating = EXCLUDED.overall_rating,
    collection_rating = EXCLUDED.collection_rating,
    quality_rating = EXCLUDED.quality_rating,
    variety_rating = EXCLUDED.variety_rating,
    quantity_rating = EXCLUDED.quantity_rating,
    comment = EXCLUDED.comment,
    visible = TRUE,
    updated_at = NOW();

INSERT INTO review_reports (
    id, review_id, reported_by_user_id, reason, status, resolution_note,
    created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-review-report-' || r.id),
    r.id,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
    'SEED_MODERATION_CHECK',
    'PENDING',
    NULL,
    NOW() - INTERVAL '3 hours',
    NOW()
FROM reviews r
JOIN orders o ON o.id = r.order_id
WHERE o.order_number LIKE 'LB-PLAY-%'
  AND CAST(RIGHT(o.order_number, 2) AS INTEGER) % 25 = 0
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    updated_at = NOW();

WITH store_seed AS (
    SELECT
        n,
        lb_seed_uuid('lb-play-store-' || n) AS store_id
    FROM generate_series(1, 1000) AS seed(n)
)
INSERT INTO store_rating_summaries (
    store_id, review_count, recent_review_count, overall_rating_avg,
    collection_rating_avg, quality_rating_avg, variety_rating_avg,
    quantity_rating_avg, updated_at
)
SELECT
    store_id,
    25 + (n % 420),
    5 + (n % 25),
    ROUND((4.10 + ((n % 9) * 0.10))::NUMERIC, 2),
    ROUND((4.00 + ((n % 8) * 0.10))::NUMERIC, 2),
    ROUND((4.05 + ((n % 8) * 0.10))::NUMERIC, 2),
    ROUND((3.95 + ((n % 8) * 0.10))::NUMERIC, 2),
    ROUND((4.00 + ((n % 9) * 0.10))::NUMERIC, 2),
    NOW()
FROM store_seed
ON CONFLICT (store_id) DO UPDATE SET
    review_count = EXCLUDED.review_count,
    recent_review_count = EXCLUDED.recent_review_count,
    overall_rating_avg = EXCLUDED.overall_rating_avg,
    collection_rating_avg = EXCLUDED.collection_rating_avg,
    quality_rating_avg = EXCLUDED.quality_rating_avg,
    variety_rating_avg = EXCLUDED.variety_rating_avg,
    quantity_rating_avg = EXCLUDED.quantity_rating_avg,
    updated_at = NOW();

INSERT INTO voucher_campaigns (
    id, owner_type, store_id, bag_id, created_by_user_id, status, name,
    description, discount_type, discount_value, max_discount_amount,
    min_order_amount, funding_source, platform_funding_bps,
    merchant_funding_bps, starts_at, ends_at, budget_limit_amount,
    total_usage_limit, per_user_limit, approved_at, published_at,
    created_at, updated_at
) VALUES
    (
        lb_seed_uuid('lb-play-voucher-campaign-welcome'),
        'PLATFORM', NULL, NULL, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
        'ACTIVE', 'Welcome test discount', 'Seed campaign for first order and voucher screens.',
        'FIXED_AMOUNT', 10000, NULL, 30000, 'PLATFORM', 10000, 0,
        NOW() - INTERVAL '7 days', NOW() + INTERVAL '45 days',
        20000000, 5000, 5, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days',
        NOW() - INTERVAL '7 days', NOW()
    ),
    (
        lb_seed_uuid('lb-play-voucher-campaign-dian30'),
        'PLATFORM', NULL, NULL, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
        'ACTIVE', 'Di An 30 percent', 'Seed city-wide promo for discovery and checkout.',
        'PERCENTAGE', 30, 20000, 50000, 'PLATFORM', 10000, 0,
        NOW() - INTERVAL '3 days', NOW() + INTERVAL '30 days',
        30000000, 5000, 3, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days', NOW()
    ),
    (
        lb_seed_uuid('lb-play-voucher-campaign-store-save'),
        'MERCHANT', lb_seed_uuid('lb-play-store-1'), lb_seed_uuid('lb-play-bag-1-1'), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
        'ACTIVE', 'Dev store saver', 'Seed merchant promo for one dev store.',
        'FIXED_AMOUNT', 5000, NULL, 20000, 'MERCHANT', 0, 10000,
        NOW() - INTERVAL '2 days', NOW() + INTERVAL '21 days',
        5000000, 1000, 3, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '2 days', NOW()
    )
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    starts_at = EXCLUDED.starts_at,
    ends_at = EXCLUDED.ends_at,
    updated_at = NOW();

INSERT INTO voucher_codes (
    id, campaign_id, code, code_type, status, usage_limit,
    starts_at, ends_at, created_at, updated_at
) VALUES
    (lb_seed_uuid('lb-play-voucher-code-lbwelcome'), lb_seed_uuid('lb-play-voucher-campaign-welcome'), 'LBWELCOME', 'PUBLIC', 'ACTIVE', 5000, NOW() - INTERVAL '7 days', NOW() + INTERVAL '45 days', NOW() - INTERVAL '7 days', NOW()),
    (lb_seed_uuid('lb-play-voucher-code-dian30'), lb_seed_uuid('lb-play-voucher-campaign-dian30'), 'DIAN30', 'PUBLIC', 'ACTIVE', 5000, NOW() - INTERVAL '3 days', NOW() + INTERVAL '30 days', NOW() - INTERVAL '3 days', NOW()),
    (lb_seed_uuid('lb-play-voucher-code-store5k'), lb_seed_uuid('lb-play-voucher-campaign-store-save'), 'STORE5K', 'PUBLIC', 'ACTIVE', 1000, NOW() - INTERVAL '2 days', NOW() + INTERVAL '21 days', NOW() - INTERVAL '2 days', NOW())
ON CONFLICT (code) DO UPDATE SET
    status = EXCLUDED.status,
    usage_limit = EXCLUDED.usage_limit,
    starts_at = EXCLUDED.starts_at,
    ends_at = EXCLUDED.ends_at,
    updated_at = NOW();

WITH customer_seed AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
),
campaign_seed AS (
    SELECT 1 AS idx, lb_seed_uuid('lb-play-voucher-campaign-welcome') AS campaign_id, lb_seed_uuid('lb-play-voucher-code-lbwelcome') AS code_id
    UNION ALL SELECT 2, lb_seed_uuid('lb-play-voucher-campaign-dian30'), lb_seed_uuid('lb-play-voucher-code-dian30')
    UNION ALL SELECT 3, lb_seed_uuid('lb-play-voucher-campaign-store-save'), lb_seed_uuid('lb-play-voucher-code-store5k')
)
INSERT INTO user_vouchers (
    id, user_id, campaign_id, voucher_code_id, status, expires_at,
    created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-user-voucher-' || customer_seed.idx || '-' || campaign_seed.idx),
    customer_seed.user_id,
    campaign_seed.campaign_id,
    campaign_seed.code_id,
    'CLAIMED',
    NOW() + INTERVAL '30 days',
    NOW() - INTERVAL '2 days',
    NOW()
FROM customer_seed
CROSS JOIN campaign_seed
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    expires_at = EXCLUDED.expires_at,
    updated_at = NOW();

INSERT INTO voucher_redemptions (
    id, order_id, user_id, campaign_id, voucher_code_id, user_voucher_id,
    code_snapshot, discount_type, discount_value, funding_source, status,
    subtotal_amount, discount_amount, platform_funded_amount,
    merchant_funded_amount, reserved_at, redeemed_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-voucher-redemption-' || o.order_number),
    o.id,
    o.user_id,
    lb_seed_uuid('lb-play-voucher-campaign-welcome'),
    lb_seed_uuid('lb-play-voucher-code-lbwelcome'),
    lb_seed_uuid('lb-play-user-voucher-' || ((ROW_NUMBER() OVER (ORDER BY o.order_number) - 1) % 21) || '-1'),
    'LBWELCOME',
    'FIXED_AMOUNT',
    5000,
    'PLATFORM',
    'REDEEMED',
    o.subtotal,
    o.discount_amount,
    o.discount_amount,
    0,
    o.created_at,
    COALESCE(o.paid_at, o.created_at + INTERVAL '5 minutes'),
    o.created_at,
    NOW()
FROM orders o
WHERE o.order_number LIKE 'LB-PLAY-%'
  AND o.discount_amount > 0
  AND o.status IN ('PAID', 'READY_FOR_PICKUP', 'PICKED_UP', 'REFUNDED')
ON CONFLICT (order_id) DO UPDATE SET
    status = EXCLUDED.status,
    discount_amount = EXCLUDED.discount_amount,
    updated_at = NOW();

WITH customer_seed AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
)
INSERT INTO user_addresses (
    id, user_id, label, full_address, lat, lng, is_default, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-user-address-' || idx),
    user_id,
    'HOME',
    (10 + idx) || ' Nguyen An Ninh, Di An, Binh Duong, Viet Nam',
    10.906700 + (idx * 0.000300),
    106.769800 + (idx * 0.000250),
    TRUE,
    NOW() - INTERVAL '10 days',
    NOW()
FROM customer_seed
ON CONFLICT (id) DO UPDATE SET
    full_address = EXCLUDED.full_address,
    lat = EXCLUDED.lat,
    lng = EXCLUDED.lng,
    is_default = TRUE,
    updated_at = NOW();

WITH customer_seed AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
)
INSERT INTO user_discovery_preferences (
    id, user_id, preferred_diet, default_location_label,
    default_lat, default_lng, default_radius_km, onboarding_status,
    created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-discovery-preference-' || idx),
    user_id,
    CASE WHEN idx % 7 = 0 THEN 'VEGAN'
         WHEN idx % 5 = 0 THEN 'VEGETARIAN'
         ELSE 'EAT_EVERYTHING' END,
    'Di An, Binh Duong',
    10.906700,
    106.769800,
    15,
    'COMPLETED',
    NOW() - INTERVAL '10 days',
    NOW()
FROM customer_seed
ON CONFLICT (user_id) DO UPDATE SET
    preferred_diet = EXCLUDED.preferred_diet,
    default_location_label = EXCLUDED.default_location_label,
    default_lat = EXCLUDED.default_lat,
    default_lng = EXCLUDED.default_lng,
    default_radius_km = EXCLUDED.default_radius_km,
    onboarding_status = EXCLUDED.onboarding_status,
    updated_at = NOW();

WITH prefs AS (
    SELECT id AS preference_id
    FROM user_discovery_preferences
    WHERE user_id IN (
        SELECT 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID
        UNION ALL
        SELECT lb_seed_uuid('lb-play-user-' || n)
        FROM generate_series(1, 20) AS tester(n)
    )
),
slots AS (
    SELECT 'MIDDAY' AS slot
    UNION ALL SELECT 'EVENING'
)
INSERT INTO user_preferred_collection_times (preference_id, slot)
SELECT preference_id, slot
FROM prefs
CROSS JOIN slots
ON CONFLICT (preference_id, slot) DO NOTHING;

WITH customer_seed AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
),
favorites AS (
    SELECT
        customer_seed.idx,
        customer_seed.user_id,
        fav_no,
        ((customer_seed.idx * 11 + fav_no) % 1000) + 1 AS store_no
    FROM customer_seed
    CROSS JOIN generate_series(1, 8) AS fav(fav_no)
)
INSERT INTO favorite_stores (id, user_id, store_id, created_at)
SELECT
    lb_seed_uuid('lb-play-favorite-store-' || idx || '-' || fav_no),
    user_id,
    lb_seed_uuid('lb-play-store-' || store_no),
    NOW() - ((fav_no + 1) || ' days')::INTERVAL
FROM favorites
ON CONFLICT (user_id, store_id) DO NOTHING;

WITH customer_seed AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
),
favorites AS (
    SELECT
        customer_seed.idx,
        customer_seed.user_id,
        fav_no,
        ((customer_seed.idx * 11 + fav_no) % 1000) + 1 AS store_no,
        CASE WHEN fav_no % 2 = 0 THEN 2 ELSE 1 END AS bag_slot
    FROM customer_seed
    CROSS JOIN generate_series(1, 8) AS fav(fav_no)
)
INSERT INTO favorite_bags (user_id, bag_id, created_at)
SELECT
    user_id,
    lb_seed_uuid('lb-play-bag-' || store_no || '-' || bag_slot),
    NOW() - ((fav_no + 1) || ' days')::INTERVAL
FROM favorites
ON CONFLICT (user_id, bag_id) DO NOTHING;

WITH today_stocks AS (
    SELECT
        stock.id AS daily_stock_id,
        stock.bag_id,
        stock.quantity,
        stock.reserved,
        stock.sold,
        bags.minimum_value,
        bags.base_sale_price,
        stores.avg_rating
    FROM bag_daily_stocks stock
    JOIN surprise_bags bags ON bags.id = stock.bag_id
    JOIN stores ON stores.id = stock.store_id
    WHERE stock.date = CURRENT_DATE
      AND bags.id IN (
          SELECT lb_seed_uuid('lb-play-bag-' || store_no || '-' || bag_slot)
          FROM generate_series(1, 1000) AS stores(store_no)
          CROSS JOIN generate_series(1, 2) AS slots(bag_slot)
      )
)
INSERT INTO bag_ranking_cache (
    daily_stock_id, bag_id, rating_score, discount_score,
    urgency_score, availability_score, computed_at
)
SELECT
    daily_stock_id,
    bag_id,
    LEAST(1, GREATEST(0, ROUND((avg_rating / 5.0)::NUMERIC, 4))),
    LEAST(1, GREATEST(0, ROUND(((minimum_value - base_sale_price) / minimum_value)::NUMERIC, 4))),
    0.7500,
    LEAST(1, GREATEST(0, ROUND(((quantity - reserved - sold) / 10.0)::NUMERIC, 4))),
    NOW()
FROM today_stocks
ON CONFLICT (daily_stock_id) DO UPDATE SET
    rating_score = EXCLUDED.rating_score,
    discount_score = EXCLUDED.discount_score,
    urgency_score = EXCLUDED.urgency_score,
    availability_score = EXCLUDED.availability_score,
    computed_at = NOW();

WITH ranked_bags AS (
    SELECT
        lb_seed_uuid('lb-play-bag-' || store_no || '-' || bag_slot) AS bag_id,
        ROW_NUMBER() OVER (ORDER BY store_no, bag_slot) AS rn
    FROM generate_series(1, 1000) AS stores(store_no)
    CROSS JOIN generate_series(1, 2) AS slots(bag_slot)
),
collections AS (
    SELECT id, slug
    FROM discovery_collections
    WHERE slug IN (
        'near_you', 'last_chance', 'big_discount', 'under_30k',
        'new_stores', 'top_rated', 'bestseller_today', 'recommended_for_you'
    )
)
INSERT INTO discovery_collection_items (
    id, collection_id, bag_id, pinned_order, added_at
)
SELECT
    lb_seed_uuid('lb-play-discovery-collection-item-' || collections.slug || '-' || ranked_bags.rn),
    collections.id,
    ranked_bags.bag_id,
    ranked_bags.rn,
    NOW()
FROM collections
JOIN ranked_bags ON ranked_bags.rn <= 50
ON CONFLICT (collection_id, bag_id) DO UPDATE SET
    pinned_order = EXCLUDED.pinned_order,
    added_at = NOW();

INSERT INTO platform_commissions (
    id, order_id, payment_id, gross_amount, platform_fee_amount,
    merchant_net_amount, rate_bps, status, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-platform-commission-' || o.order_number),
    o.id,
    p.id,
    o.final_amount,
    o.platform_fee,
    GREATEST(o.final_amount - o.platform_fee, 0),
    0,
    CASE WHEN o.status = 'REFUNDED' THEN 'REVERSED'
         WHEN o.status IN ('PAID', 'READY_FOR_PICKUP', 'PICKED_UP') THEN 'EARNED'
         ELSE 'PENDING' END,
    o.created_at,
    NOW()
FROM orders o
JOIN payments p ON p.order_id = o.id
WHERE o.order_number LIKE 'LB-PLAY-%'
ON CONFLICT (order_id) DO UPDATE SET
    payment_id = EXCLUDED.payment_id,
    gross_amount = EXCLUDED.gross_amount,
    platform_fee_amount = EXCLUDED.platform_fee_amount,
    merchant_net_amount = EXCLUDED.merchant_net_amount,
    status = EXCLUDED.status,
    updated_at = NOW();

WITH settlement_seed AS (
    SELECT
        n,
        lb_seed_uuid('lb-play-store-' || n) AS store_id,
        500000 + (n * 25000) AS gross_amount,
        40000 + (n * 1000) AS commission_amount
    FROM generate_series(1, 20) AS seed(n)
)
INSERT INTO merchant_settlements (
    id, business_profile_id, store_id, period_start, period_end,
    gross_amount, commission_amount, refund_amount, net_amount,
    status, approved_at, paid_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-settlement-' || n),
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001'::UUID,
    store_id,
    CURRENT_DATE - 30,
    CURRENT_DATE - 1,
    gross_amount,
    commission_amount,
    CASE WHEN n % 5 = 0 THEN 20000 ELSE 0 END,
    gross_amount - commission_amount - CASE WHEN n % 5 = 0 THEN 20000 ELSE 0 END,
    'PAID',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '3 days',
    NOW()
FROM settlement_seed
ON CONFLICT (id) DO UPDATE SET
    gross_amount = EXCLUDED.gross_amount,
    commission_amount = EXCLUDED.commission_amount,
    refund_amount = EXCLUDED.refund_amount,
    net_amount = EXCLUDED.net_amount,
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at,
    updated_at = NOW();

INSERT INTO store_payouts (
    id, settlement_id, store_id, bank_account_id, provider,
    idempotency_key, amount, status, provider_payout_id,
    provider_transaction_id, requested_at, paid_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-payout-' || ms.id),
    ms.id,
    ms.store_id,
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0002'::UUID,
    'PAYOS',
    'lb-play-payout:' || ms.id,
    ms.net_amount,
    'PAID',
    'lb-play-payout-' || RIGHT(ms.id::TEXT, 12),
    'lb-play-payout-txn-' || RIGHT(ms.id::TEXT, 12),
    ms.approved_at,
    ms.paid_at,
    ms.created_at,
    NOW()
FROM merchant_settlements ms
WHERE ms.id IN (
    SELECT lb_seed_uuid('lb-play-settlement-' || n)
    FROM generate_series(1, 20) AS seed(n)
)
ON CONFLICT (idempotency_key) DO UPDATE SET
    amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at,
    updated_at = NOW();

WITH seed_users AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL SELECT -1, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID
    UNION ALL SELECT -2, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
)
INSERT INTO notification_devices (
    id, user_id, device_token, device_type, app_version,
    is_active, last_seen_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-notification-device-' || idx),
    user_id,
    'seed-device-token-' || idx,
    CASE WHEN idx % 3 = 0 THEN 'ANDROID' WHEN idx % 3 = 1 THEN 'IOS' ELSE 'WEB' END,
    '1.0.0-seed',
    TRUE,
    NOW(),
    NOW() - INTERVAL '7 days',
    NOW()
FROM seed_users
ON CONFLICT (user_id, device_token) DO UPDATE SET
    is_active = TRUE,
    last_seen_at = NOW(),
    updated_at = NOW();

WITH seed_users AS (
    SELECT 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL SELECT 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID
    UNION ALL SELECT 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID
    UNION ALL
    SELECT lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
),
categories AS (
    SELECT UNNEST(ARRAY['ORDER', 'PICKUP', 'PROMOTION', 'STORE', 'MERCHANT', 'SYSTEM']) AS category
)
INSERT INTO notification_preferences (
    id, user_id, category, push_enabled, email_enabled, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-notification-preference-' || user_id || '-' || category),
    user_id,
    category,
    TRUE,
    category IN ('ORDER', 'SYSTEM'),
    NOW() - INTERVAL '7 days',
    NOW()
FROM seed_users
CROSS JOIN categories
ON CONFLICT (user_id, category) DO UPDATE SET
    push_enabled = TRUE,
    email_enabled = EXCLUDED.email_enabled,
    updated_at = NOW();

WITH seed_users AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL SELECT -1, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID
    UNION ALL SELECT -2, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
),
notification_seed AS (
    SELECT seed_users.idx, seed_users.user_id, msg_no
    FROM seed_users
    CROSS JOIN generate_series(1, 3) AS messages(msg_no)
)
INSERT INTO notifications (
    id, recipient_id, type, category, title, body, image_url, deep_link,
    reference_type, reference_id, payload, dedupe_key, is_read, read_at,
    created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-notification-' || idx || '-' || msg_no),
    user_id,
    CASE msg_no
        WHEN 1 THEN 'SYSTEM_NOTICE'
        WHEN 2 THEN 'VOUCHER_AVAILABLE'
        ELSE CASE WHEN idx < 0 THEN 'MERCHANT_DAILY_SUMMARY' ELSE 'ORDER_READY_FOR_PICKUP' END
    END,
    CASE msg_no
        WHEN 1 THEN 'SYSTEM'
        WHEN 2 THEN 'PROMOTION'
        ELSE CASE WHEN idx < 0 THEN 'MERCHANT' ELSE 'ORDER' END
    END,
    CASE msg_no
        WHEN 1 THEN 'Seed account ready'
        WHEN 2 THEN 'Voucher ready for testing'
        ELSE CASE WHEN idx < 0 THEN 'Daily store summary ready' ELSE 'Order ready for pickup' END
    END,
    CASE msg_no
        WHEN 1 THEN 'This seeded account is ready for LastBite testing.'
        WHEN 2 THEN 'Use LBBWELCOME, DIAN30 or STORE5K in checkout tests.'
        ELSE CASE WHEN idx < 0 THEN 'Seed stores have fresh orders, stock and payout data.' ELSE 'Open the app to test pickup and order detail screens.' END
    END,
    NULL,
    CASE msg_no
        WHEN 2 THEN 'lastbite://vouchers'
        ELSE 'lastbite://home'
    END,
    CASE msg_no WHEN 2 THEN 'CAMPAIGN' ELSE 'SYSTEM' END,
    CASE msg_no WHEN 2 THEN lb_seed_uuid('lb-play-voucher-campaign-welcome') ELSE NULL END,
    jsonb_build_object('seed', TRUE, 'messageNo', msg_no),
    'lb-play-notification-' || idx || '-' || msg_no,
    msg_no = 1,
    CASE WHEN msg_no = 1 THEN NOW() - INTERVAL '1 day' ELSE NULL END,
    NOW() - ((msg_no + 1) || ' days')::INTERVAL,
    NOW()
FROM notification_seed
ON CONFLICT (dedupe_key) DO UPDATE SET
    type = EXCLUDED.type,
    category = EXCLUDED.category,
    title = EXCLUDED.title,
    body = EXCLUDED.body,
    payload = EXCLUDED.payload,
    is_read = EXCLUDED.is_read,
    read_at = EXCLUDED.read_at,
    updated_at = NOW();

INSERT INTO notification_deliveries (
    id, notification_id, device_id, channel, status, provider_message_id,
    retry_count, sent_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-notification-delivery-' || n.id),
    n.id,
    d.id,
    'FCM',
    'SKIPPED',
    NULL,
    0,
    NULL,
    n.created_at,
    NOW()
FROM notifications n
LEFT JOIN notification_devices d ON d.user_id = n.recipient_id AND d.is_active = TRUE
WHERE n.dedupe_key LIKE 'lb-play-notification-%'
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    device_id = EXCLUDED.device_id,
    updated_at = NOW();

INSERT INTO stock_audit_logs (
    id, bag_id, daily_stock_id, actor_id, action, delta,
    quantity_before, quantity_after, reason, order_id, actor_type, created_at
)
SELECT
    lb_seed_uuid('lb-play-stock-audit-set-' || stock.id),
    stock.bag_id,
    stock.id,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'::UUID,
    'STOCK_SET',
    stock.quantity,
    0,
    stock.quantity,
    'Seed weekly stock plan generated.',
    NULL,
    'MERCHANT',
    stock.created_at
FROM bag_daily_stocks stock
WHERE stock.date = CURRENT_DATE
  AND stock.bag_id IN (
      SELECT lb_seed_uuid('lb-play-bag-' || store_no || '-' || bag_slot)
      FROM generate_series(1, 500) AS stores(store_no)
      CROSS JOIN generate_series(1, 2) AS slots(bag_slot)
  )
ON CONFLICT (id) DO NOTHING;

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-play-store-' || n) AS store_id
    FROM generate_series(1, 20) AS seed(n)
)
INSERT INTO store_closure_days (
    id, store_id, closed_date, reason, created_by_user_id, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-store-closure-' || n),
    store_id,
    CURRENT_DATE + 10 + n,
    'Seed closure day for calendar testing.',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
    NOW(),
    NOW()
FROM store_seed
ON CONFLICT (store_id, closed_date) DO UPDATE SET
    reason = EXCLUDED.reason,
    updated_at = NOW();

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-play-store-' || n) AS store_id
    FROM generate_series(21, 40) AS seed(n)
)
INSERT INTO store_special_hours (
    id, store_id, special_date, open_time, close_time, is_closed,
    reason, created_by_user_id, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-store-special-hour-' || n),
    store_id,
    CURRENT_DATE + 7,
    '09:00'::TIME,
    '18:00'::TIME,
    FALSE,
    'Seed holiday special hours.',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
    NOW(),
    NOW()
FROM store_seed
ON CONFLICT (store_id, special_date) DO UPDATE SET
    open_time = EXCLUDED.open_time,
    close_time = EXCLUDED.close_time,
    is_closed = FALSE,
    reason = EXCLUDED.reason,
    updated_at = NOW();

INSERT INTO admin_notes (
    id, target_type, target_id, actor_user_id, note, created_at, updated_at
) VALUES
    (
        lb_seed_uuid('lb-play-admin-note-business-profile'),
        'BUSINESS_PROFILE',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001'::UUID,
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
        'Seed business profile used for full app testing.',
        NOW(),
        NOW()
    ),
    (
        lb_seed_uuid('lb-play-admin-note-store-1'),
        'STORE',
        lb_seed_uuid('lb-play-store-1'),
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002'::UUID,
        'Seed dev store with manager account dev_store.',
        NOW(),
        NOW()
    )
ON CONFLICT (id) DO UPDATE SET
    note = EXCLUDED.note,
    updated_at = NOW();

INSERT INTO account_deletion_requests (
    id, user_id, request_email, request_phone, requester_type, source,
    status, reason, blocker_summary, token_hash, token_expires_at,
    verified_at, scheduled_deletion_at, created_at, updated_at
) VALUES (
    lb_seed_uuid('lb-play-account-deletion-cancelled'),
    lb_seed_uuid('lb-play-user-20'),
    'playtester20@lastbite.test',
    '0988000120',
    'CUSTOMER',
    'IN_APP',
    'CANCELLED',
    'Seed cancelled deletion request for compliance screen testing.',
    NULL,
    'lb-play-cancelled-deletion-token-hash',
    NOW() + INTERVAL '1 day',
    NOW() - INTERVAL '1 day',
    NULL,
    NOW() - INTERVAL '1 day',
    NOW()
)
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    reason = EXCLUDED.reason,
    updated_at = NOW();

DELETE FROM store_engagement_events
WHERE source LIKE 'play_seed_%';

WITH event_seed AS (
    SELECT
        n,
        ((n - 1) % 1000) + 1 AS store_no,
        CASE WHEN n % 2 = 0 THEN 2 ELSE 1 END AS bag_slot,
        ((n - 1) % 21) AS user_idx
    FROM generate_series(1, 30000) AS events(n)
),
customer_seed AS (
    SELECT 0 AS idx, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001'::UUID AS user_id
    UNION ALL
    SELECT n, lb_seed_uuid('lb-play-user-' || n)
    FROM generate_series(1, 20) AS tester(n)
)
INSERT INTO store_engagement_events (
    store_id, bag_id, user_id, event_type, source, session_id,
    referrer, user_agent, ip_hash, occurred_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-play-store-' || event_seed.store_no),
    lb_seed_uuid('lb-play-bag-' || event_seed.store_no || '-' || event_seed.bag_slot),
    CASE WHEN event_seed.n % 5 = 0 THEN NULL ELSE customer_seed.user_id END,
    CASE
        WHEN event_seed.n % 4 = 0 THEN 'STORE_VIEW'
        WHEN event_seed.n % 4 = 1 THEN 'STORE_CARD_CLICK'
        WHEN event_seed.n % 4 = 2 THEN 'BAG_VIEW'
        ELSE 'BAG_CARD_CLICK'
    END,
    CASE
        WHEN event_seed.n % 3 = 0 THEN 'play_seed_home'
        WHEN event_seed.n % 3 = 1 THEN 'play_seed_nearby'
        ELSE 'play_seed_store_detail'
    END,
    'play-seed-session-' || (event_seed.n % 500),
    'lastbite://seed',
    'LastBiteSeed/1.0',
    MD5('seed-ip-' || (event_seed.n % 200)),
    NOW() - ((event_seed.n % 10080) || ' minutes')::INTERVAL,
    NOW(),
    NOW()
FROM event_seed
LEFT JOIN customer_seed ON customer_seed.idx = event_seed.user_idx;

DROP FUNCTION IF EXISTS lb_seed_uuid(TEXT);
