-- V30 - Vung Tau marketplace seed.
-- Creates a deterministic, idempotent seed dataset for discovery, map search,
-- store detail, bag stock, and merchant review flows.

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
        (lb_seed_uuid('lb-vung-tau-user-merchant-owner'), 'vungtau.merchant@lastbite.test', 'vungtau_merchant', 'Vung Tau Merchant Owner', '0989000001', 'PLATFORM', 'MERCHANT_OWNER'),
        (lb_seed_uuid('lb-vung-tau-user-store-manager'), 'vungtau.manager@lastbite.test', 'vungtau_manager', 'Vung Tau Store Manager', '0989000002', 'STORE_MEMBER', 'MANAGER')
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
    NOW() - INTERVAL '28 days',
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
    auth_provider = EXCLUDED.auth_provider,
    email_verified = TRUE,
    phone_verified = TRUE,
    must_change_password = FALSE,
    updated_at = NOW();

WITH seed_user_roles(user_id, role_code) AS (
    VALUES
        (lb_seed_uuid('lb-vung-tau-user-merchant-owner'), 'MERCHANT_OWNER'),
        (lb_seed_uuid('lb-vung-tau-user-store-manager'), 'MANAGER')
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
    lb_seed_uuid('lb-vung-tau-business-profile'),
    lb_seed_uuid('lb-vung-tau-user-merchant-owner'),
    'HOUSEHOLD_BUSINESS',
    'LastBite Vung Tau Seed Group',
    'Vung Tau Merchant Owner',
    '0989000001',
    'vungtau.merchant@lastbite.test',
    'CCCD',
    '077200000100',
    '3500000100',
    'DKKD-VUNGTAU-0100',
    'Vung Tau, Ba Ria - Vung Tau, Viet Nam',
    'APPROVED',
    NOW() - INTERVAL '25 days',
    NOW() - INTERVAL '30 days',
    NOW()
)
ON CONFLICT (id) DO UPDATE SET
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
    lb_seed_uuid('lb-vung-tau-business-profile-version-1'),
    lb_seed_uuid('lb-vung-tau-business-profile'),
    1,
    '{"seed":true,"legalName":"LastBite Vung Tau Seed Group","city":"vung-tau","storeCount":100}'::TEXT,
    'APPROVED',
    NOW() - INTERVAL '26 days',
    NOW() - INTERVAL '25 days',
    NOW() - INTERVAL '26 days',
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
    lb_seed_uuid('lb-vung-tau-bank-account'),
    lb_seed_uuid('lb-vung-tau-business-profile'),
    'VCB',
    'Vietcombank',
    'LASTBITE VUNG TAU SEED GROUP',
    'seed-encrypted-bank-account-vung-tau-0100',
    '0100',
    TRUE,
    'APPROVED',
    NOW() - INTERVAL '25 days',
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
        lb_seed_uuid('lb-vung-tau-store-' || n) AS store_id,
        (ARRAY['BAKERY', 'CAFE', 'RESTAURANT', 'GROCERY', 'CONVENIENCE'])[((n - 1) % 5) + 1] AS category,
        (ARRAY[
            'Phuong 1', 'Phuong 2', 'Phuong 3', 'Phuong 4', 'Phuong 5',
            'Phuong 7', 'Phuong 8', 'Phuong 10', 'Phuong 11', 'Phuong 12',
            'Thang Nhat', 'Thang Nhi', 'Rach Dua', 'Nguyen An Ninh'
        ])[((n - 1) % 14) + 1] AS district,
        (ARRAY[
            'Ha Long', 'Thuy Van', 'Ba Cu', 'Le Hong Phong', 'Nguyen An Ninh',
            'Truong Cong Dinh', 'Nam Ky Khoi Nghia', 'Hoang Hoa Tham',
            'Phan Chu Trinh', 'Le Loi', 'Nguyen Thai Hoc', 'Do Chieu',
            'Binh Gia', 'Nguyen Huu Canh', '30 Thang 4', '2 Thang 9',
            'Tran Phu', 'Vo Thi Sau'
        ])[((n - 1) % 18) + 1] AS street
    FROM generate_series(1, 100) AS seed(n)
)
INSERT INTO stores (
    id, created_by_user_id, business_profile_id, name, slug, description, category,
    phone, email, address, district, city, lat, lng, pickup_instructions,
    cover_image_url, logo_url, storefront_image_url, menu_image_url,
    status, verification_status, avg_rating, total_ratings, created_at, updated_at
)
SELECT
    store_id,
    lb_seed_uuid('lb-vung-tau-user-merchant-owner'),
    lb_seed_uuid('lb-vung-tau-business-profile'),
    CASE category
        WHEN 'BAKERY' THEN (ARRAY['Lo Banh Bai Sau', 'Banh Mi Hai Dang', 'Tiem Banh Song Bien', 'Sweet Dock Bakery'])[((n - 1) / 5 % 4) + 1]
        WHEN 'CAFE' THEN (ARRAY['Cafe Mui Nghinh Phong', 'Roastery Bai Truoc', 'Sea Breeze Coffee', 'Ben Tau Espresso'])[((n - 1) / 5 % 4) + 1]
        WHEN 'RESTAURANT' THEN (ARRAY['Com Nha Vung Tau', 'Bep Hai San Nho', 'Pho Nui Lon', 'Quan An Bai Dau'])[((n - 1) / 5 % 4) + 1]
        WHEN 'GROCERY' THEN (ARRAY['Cho Nho Vung Tau', 'Fresh Mart Bai Sau', 'Green Basket VT', 'Local Pantry Vung Tau'])[((n - 1) / 5 % 4) + 1]
        ELSE (ARRAY['Mini Stop Bien Xanh', 'Corner Mart Vung Tau', 'Quick Basket VT', 'Everyday Mart Bai Sau'])[((n - 1) / 5 % 4) + 1]
    END || ' ' || LPAD(n::TEXT, 3, '0'),
    'lb-vung-tau-store-' || LPAD(n::TEXT, 3, '0'),
    CASE category
        WHEN 'BAKERY' THEN 'Seed bakery in Vung Tau with end-of-day bread and pastry rescue bags.'
        WHEN 'CAFE' THEN 'Seed cafe in Vung Tau with coffee, pastry, and light meal surplus bags.'
        WHEN 'RESTAURANT' THEN 'Seed restaurant in Vung Tau with lunch and dinner meal rescue bags.'
        WHEN 'GROCERY' THEN 'Seed grocery store in Vung Tau with produce and pantry rescue bags.'
        ELSE 'Seed convenience store in Vung Tau with daily mixed surplus bags.'
    END,
    category,
    '0254' || LPAD((700000 + n)::TEXT, 6, '0'),
    'vungtau.store' || LPAD(n::TEXT, 3, '0') || '@lastbite.test',
    (10 + (n % 190)) || ' ' || street || ', ' || district || ', Vung Tau, Ba Ria - Vung Tau, Viet Nam',
    district,
    'vung-tau',
    10.335000 + (((n - 1) % 20) * 0.003200) + (((n - 1) / 20) * 0.001200),
    107.064000 + (((n - 1) % 10) * 0.004800) + (((n - 1) / 10) * 0.000700),
    'Show pickup code or QR to staff at the counter. Bring your own bag when possible.',
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
        ELSE 'https://images.unsplash.com/photo-1580913428706-c311e67898b3'
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
    ROUND((4.15 + ((n % 8) * 0.10))::NUMERIC, 2),
    18 + (n % 260),
    NOW() - ((n % 90) || ' days')::INTERVAL,
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
    SELECT n, lb_seed_uuid('lb-vung-tau-store-' || n) AS store_id
    FROM generate_series(1, 100) AS seed(n)
)
INSERT INTO store_schedules (
    id, store_id, day_of_week, open_time, close_time, is_open, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-vung-tau-store-schedule-' || n || '-' || day_of_week),
    store_id,
    day_of_week,
    CASE WHEN n % 5 IN (0, 1) THEN '06:30'::TIME ELSE '08:00'::TIME END,
    CASE WHEN n % 4 = 0 THEN '22:30'::TIME ELSE '21:30'::TIME END,
    TRUE,
    NOW() - INTERVAL '25 days',
    NOW()
FROM store_seed
CROSS JOIN generate_series(0, 6) AS days(day_of_week)
ON CONFLICT (store_id, day_of_week) DO UPDATE SET
    open_time = EXCLUDED.open_time,
    close_time = EXCLUDED.close_time,
    is_open = TRUE,
    updated_at = NOW();

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-vung-tau-store-' || n) AS store_id
    FROM generate_series(1, 100) AS seed(n)
)
INSERT INTO store_reliability_stats (
    store_id, total_bags_listed, total_bags_sold, total_bags_fulfilled,
    total_bags_no_show, fulfillment_rate, warning_count, is_under_review,
    merchant_cancelled_count, store_fault_refund_count, last_recalculated_at,
    created_at, updated_at
)
SELECT
    store_id,
    80 + (n % 220),
    42 + (n % 170),
    40 + (n % 160),
    n % 4,
    ROUND((0.93 + ((n % 6) * 0.01))::NUMERIC, 2),
    n % 2,
    FALSE,
    n % 3,
    n % 2,
    NOW(),
    NOW() - INTERVAL '25 days',
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
    lb_seed_uuid('lb-vung-tau-store-manager-membership'),
    lb_seed_uuid('lb-vung-tau-user-store-manager'),
    lb_seed_uuid('lb-vung-tau-store-1'),
    roles.id,
    'ACTIVE',
    lb_seed_uuid('lb-vung-tau-user-merchant-owner'),
    NOW() - INTERVAL '24 days',
    NOW() - INTERVAL '24 days',
    NOW()
FROM roles
WHERE roles.code = 'MANAGER'
ON CONFLICT (id) DO UPDATE SET
    store_id = EXCLUDED.store_id,
    role_id = EXCLUDED.role_id,
    status = 'ACTIVE',
    updated_at = NOW();

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-vung-tau-store-' || n) AS store_id
    FROM generate_series(1, 100) AS seed(n)
)
INSERT INTO store_versions (
    id, store_id, version_number, snapshot_json, review_status,
    submitted_at, reviewed_at, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-vung-tau-store-version-' || n || '-1'),
    store_id,
    1,
    jsonb_build_object('seed', TRUE, 'storeNo', n, 'city', 'vung-tau')::TEXT,
    'APPROVED',
    NOW() - INTERVAL '24 days',
    NOW() - INTERVAL '23 days',
    NOW() - INTERVAL '24 days',
    NOW()
FROM store_seed
ON CONFLICT (store_id, version_number) DO UPDATE SET
    snapshot_json = EXCLUDED.snapshot_json,
    review_status = EXCLUDED.review_status,
    reviewed_at = EXCLUDED.reviewed_at,
    updated_at = NOW();

WITH store_seed AS (
    SELECT n, lb_seed_uuid('lb-vung-tau-store-' || n) AS store_id
    FROM generate_series(1, 100) AS seed(n)
)
INSERT INTO store_review_applications (
    id, store_id, business_profile_version_id, store_version_id, status,
    submitted_by, submitted_at, reviewed_at, decision_note, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-vung-tau-store-review-application-' || n),
    store_id,
    lb_seed_uuid('lb-vung-tau-business-profile-version-1'),
    lb_seed_uuid('lb-vung-tau-store-version-' || n || '-1'),
    'APPROVED',
    lb_seed_uuid('lb-vung-tau-user-merchant-owner'),
    NOW() - INTERVAL '24 days',
    NOW() - INTERVAL '23 days',
    'Seed approved for Vung Tau marketplace testing.',
    NOW() - INTERVAL '24 days',
    NOW()
FROM store_seed
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    decision_note = EXCLUDED.decision_note,
    updated_at = NOW();

WITH store_seed AS (
    SELECT
        n AS store_no,
        lb_seed_uuid('lb-vung-tau-store-' || n) AS store_id,
        (ARRAY['BAKERY', 'CAFE', 'RESTAURANT', 'GROCERY', 'CONVENIENCE'])[((n - 1) % 5) + 1] AS category
    FROM generate_series(1, 100) AS seed(n)
),
bag_seed AS (
    SELECT
        store_no,
        store_id,
        bag_slot,
        lb_seed_uuid('lb-vung-tau-bag-' || store_no || '-' || bag_slot) AS bag_id,
        category,
        CASE
            WHEN category = 'BAKERY' THEN 'BREAD'
            WHEN category = 'RESTAURANT' THEN 'MEAL'
            WHEN category = 'GROCERY' THEN 'GROCERY'
            ELSE 'MIXED'
        END AS bag_type,
        CASE
            WHEN bag_slot = 1 AND category IN ('BAKERY', 'CAFE', 'GROCERY', 'CONVENIENCE') THEN 'VEGETARIAN'
            WHEN bag_slot = 2 AND store_no % 8 = 0 THEN 'VEGAN'
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
         THEN INITCAP(LOWER(bag_seed.category)) || ' Vung Tau day rescue bag ' || LPAD(bag_seed.store_no::TEXT, 3, '0')
         ELSE INITCAP(LOWER(bag_seed.category)) || ' Vung Tau evening surprise bag ' || LPAD(bag_seed.store_no::TEXT, 3, '0') END,
    CASE bag_seed.category
        WHEN 'BAKERY' THEN 'Bread, pastry, and dessert surplus from a verified Vung Tau bakery.'
        WHEN 'CAFE' THEN 'Cafe pastry, light meal, and drink-side surplus from Vung Tau.'
        WHEN 'RESTAURANT' THEN 'Prepared meal rescue bag from a verified Vung Tau restaurant.'
        WHEN 'GROCERY' THEN 'Fresh produce and pantry surplus from a verified Vung Tau grocery store.'
        ELSE 'Mixed daily surplus from a verified Vung Tau convenience store.'
    END,
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
         ELSE 'May contain meat, egg, dairy, seafood or nuts depending on daily surplus.' END,
    CASE
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'CAFE' THEN '07:00'::TIME
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'BAKERY' THEN '10:00'::TIME
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'RESTAURANT' THEN '11:00'::TIME
        WHEN bag_seed.bag_slot = 1 THEN '15:00'::TIME
        WHEN bag_seed.category = 'RESTAURANT' THEN '19:30'::TIME
        ELSE '18:00'::TIME
    END,
    CASE
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'CAFE' THEN '09:30'::TIME
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'BAKERY' THEN '12:30'::TIME
        WHEN bag_seed.bag_slot = 1 AND bag_seed.category = 'RESTAURANT' THEN '14:00'::TIME
        WHEN bag_seed.bag_slot = 1 THEN '17:30'::TIME
        WHEN bag_seed.category = 'RESTAURANT' THEN '22:30'::TIME
        ELSE '21:00'::TIME
    END,
    ARRAY[0,1,2,3,4,5,6],
    ARRAY[
        6 + (bag_seed.store_no % 7),
        8 + (bag_seed.store_no % 7),
        9 + (bag_seed.store_no % 8),
        10 + (bag_seed.store_no % 8),
        12 + (bag_seed.store_no % 9),
        14 + (bag_seed.store_no % 10),
        10 + (bag_seed.store_no % 8)
    ],
    'ACTIVE',
    1,
    NOW() - ((bag_seed.store_no % 60) || ' days')::INTERVAL,
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
    dynamic_pricing_enabled = EXCLUDED.dynamic_pricing_enabled,
    platform_fee = EXCLUDED.platform_fee,
    max_per_order = EXCLUDED.max_per_order,
    container_provided = EXCLUDED.container_provided,
    carrier_bag_provided = EXCLUDED.carrier_bag_provided,
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
        lb_seed_uuid('lb-vung-tau-store-' || store_no) AS store_id,
        lb_seed_uuid('lb-vung-tau-bag-' || store_no || '-' || bag_slot) AS bag_id
    FROM generate_series(1, 100) AS stores(store_no)
    CROSS JOIN generate_series(1, 2) AS slots(bag_slot)
),
raw_stock AS (
    SELECT
        store_no,
        bag_slot,
        store_id,
        bag_id,
        day_offset,
        CURRENT_DATE + day_offset AS stock_date,
        8 + ((store_no + bag_slot + day_offset + 70) % 15) AS quantity
    FROM bag_seed
    CROSS JOIN generate_series(-7, 14) AS offsets(day_offset)
),
stock_seed AS (
    SELECT
        *,
        CASE
            WHEN day_offset < 0 THEN LEAST(quantity, 4 + ((store_no + bag_slot + day_offset + 100) % 8))
            WHEN day_offset = 0 THEN (store_no + bag_slot) % 3
            ELSE 0
        END AS sold,
        CASE WHEN day_offset = 0 THEN (store_no + bag_slot) % 2 ELSE 0 END AS reserved
    FROM raw_stock
)
INSERT INTO bag_daily_stocks (
    id, bag_id, store_id, date, quantity, reserved, sold, status,
    version, stock_source, created_at, updated_at
)
SELECT
    lb_seed_uuid('lb-vung-tau-stock-' || store_no || '-' || bag_slot || '-' || stock_date::TEXT),
    bag_id,
    store_id,
    stock_date,
    quantity,
    reserved,
    sold,
    CASE WHEN day_offset < 0 THEN 'EXPIRED' ELSE 'ACTIVE' END,
    1,
    'WEEKLY_DEFAULT',
    NOW() - INTERVAL '7 days',
    NOW()
FROM stock_seed
ON CONFLICT (bag_id, date) DO UPDATE SET
    quantity = EXCLUDED.quantity,
    reserved = EXCLUDED.reserved,
    sold = EXCLUDED.sold,
    status = EXCLUDED.status,
    stock_source = EXCLUDED.stock_source,
    updated_at = NOW();

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
          SELECT lb_seed_uuid('lb-vung-tau-bag-' || store_no || '-' || bag_slot)
          FROM generate_series(1, 100) AS stores(store_no)
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
    0.7000,
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
        lb_seed_uuid('lb-vung-tau-bag-' || store_no || '-' || bag_slot) AS bag_id,
        ROW_NUMBER() OVER (ORDER BY store_no, bag_slot) AS rn
    FROM generate_series(1, 100) AS stores(store_no)
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
    lb_seed_uuid('lb-vung-tau-discovery-collection-item-' || collections.slug || '-' || ranked_bags.rn),
    collections.id,
    ranked_bags.bag_id,
    ranked_bags.rn,
    NOW()
FROM collections
JOIN ranked_bags ON ranked_bags.rn <= 40
ON CONFLICT (collection_id, bag_id) DO UPDATE SET
    pinned_order = EXCLUDED.pinned_order,
    added_at = NOW();
