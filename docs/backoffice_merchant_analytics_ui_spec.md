# LastBite Back Office, Merchant Portal, Analytics UI Spec

**Mục đích:** tài liệu này gom lại các field dữ liệu, màn hình UI, metric và action cần có để thiết kế:

- **LastBite Back Office / Admin Console:** trang nội bộ cho team LastBite quản lý toàn hệ thống.
- **Merchant Portal / Partner Dashboard:** trang cho cửa hàng/merchant quản lý store, bag, order, pickup, doanh thu.
- **Analytics / BI Dashboard:** trang hoặc module cho data analyst/founder xem số liệu, funnel, báo cáo, export data.

**Nguồn rà soát:** `docs/frontend_business_flows.md`, `docs/database-reference.local.dbml`, controller/DTO/entity hiện có trong backend.

## 1. Cách Gọi Thuật Ngữ Có Đúng Không?

Đúng. Cách gọi bạn đưa ra là đúng bản chất sản phẩm:

| Tên | Ai dùng | Mục đích |
| --- | --- | --- |
| LastBite Back Office / Admin Console | Admin, operations, support, finance team LastBite | Duyệt merchant/store, kiểm soát giao dịch, xử lý refund, payout, notification, audit |
| Merchant Portal / Partner Dashboard | Merchant owner, manager, staff | Quản lý store, surprise bag, tồn kho, order, pickup, rating, doanh thu/payout |
| Analytics / BI Dashboard | Data analyst, founder, growth/ops | Xem số liệu, funnel, hiệu suất marketplace, export report |

Điểm cần nhớ: đây **không chỉ là trang cho data analyst**. Nó là một hệ thống quản trị nhiều role. Data analyst chỉ nên có quyền read-only/export trong khu Analytics, không nên có quyền duyệt store, hoàn tiền, payout hay sửa dữ liệu vận hành.

Kiến trúc route nên dùng:

| Route | Khu vực |
| --- | --- |
| `/merchant` | Merchant Portal |
| `/admin` | Admin Console / Back Office |
| `/analytics` | Analytics / BI Dashboard |

Role hiện có trong backend: `CUSTOMER`, `MERCHANT_OWNER`, `MANAGER`, `STAFF`, `ADMIN`. Các role như `ANALYST`, `SUPPORT`, `FINANCE` là đề xuất UI/sản phẩm, backend hiện chưa có role riêng.

## 2. Trạng Thái Backend Hiện Có

Backend hiện đã có phần lớn data và API cho Merchant Portal và Admin Console. Riêng merchant store engagement analytics đã có event table/API cho store/bag view và card click; admin/global BI aggregate/export vẫn nên bổ sung riêng. Khi làm UI analytics, có 2 hướng:

1. MVP: frontend/admin dùng API hiện có để list dữ liệu và tính đơn giản ở client.
2. Tốt hơn: thêm endpoint aggregate riêng như `/api/v1/admin/analytics/overview`, `/sales`, `/orders`, `/merchants`, `/refunds`, `/export`.

## 3. Data Dictionary Cho UI

Phần này chỉ liệt kê các table/field quan trọng cho Admin, Merchant và Analytics. Không cần show hết field kỹ thuật trên UI, nhưng nên biết field nào tồn tại để map màn hình.

### 3.1 Users, Roles, Staff

Table `users`:

| Field | Dùng cho UI |
| --- | --- |
| `id` | user id |
| `email` | email platform account, nullable với store member |
| `username` | username cho manager/staff store login |
| `full_name` | tên hiển thị |
| `phone` | số điện thoại |
| `avatar_url` | avatar |
| `account_type` | `PLATFORM`, `STORE_MEMBER` |
| `status` | `ACTIVE`, `INACTIVE`, `BANNED` |
| `auth_provider` | `LOCAL`, `GOOGLE`, `ZALO` |
| `email_verified`, `phone_verified` | trạng thái xác minh |
| `must_change_password` | bắt đổi mật khẩu lần đầu cho staff |
| `last_login_at`, `created_at`, `updated_at` | audit/analytics |

Table `roles`: `code`, `scope`. Role code hiện có: `CUSTOMER`, `MERCHANT_OWNER`, `ADMIN`, `MANAGER`, `STAFF`.

Table `merchant_store_members`:

| Field | Dùng cho UI |
| --- | --- |
| `id`, `user_id`, `store_id` | định danh member |
| `role_id` | role manager/staff |
| `status` | `ACTIVE`, `SUSPENDED`, `TERMINATED` |
| `created_by_user_id` | ai tạo |
| `joined_at`, `created_at`, `updated_at` | lịch sử |

UI Staff Management nên hiển thị: username, full name, phone, role, status, must change password, joined at, action suspend.

### 3.2 Store

Table `stores`:

| Field | Dùng cho UI |
| --- | --- |
| `id` | store id |
| `created_by_user_id` | merchant owner tạo store |
| `business_profile_id` | business profile liên kết |
| `name`, `slug`, `description` | thông tin public |
| `category` | `BAKERY`, `RESTAURANT`, `CAFE`, `GROCERY`, `CONVENIENCE` |
| `phone`, `email` | liên hệ store |
| `address`, `district`, `city`, `lat`, `lng` | địa chỉ/map pin |
| `pickup_instructions` | hướng dẫn nhận hàng |
| `storefront_image_url`, `menu_image_url`, `cover_image_url`, `logo_url` | ảnh public |
| `business_license_number`, `business_license_image_url` | legacy/simple license fields |
| `status` | `DRAFT`, `ACTIVE`, `PAUSED`, `CLOSED`, `SUSPENDED` |
| `verification_status` | `DRAFT`, `PENDING`, `CHANGES_REQUESTED`, `VERIFIED`, `REJECTED` |
| `rejection_reason` | lý do từ chối |
| `avg_rating`, `total_ratings` | rating summary nhanh |
| `created_at`, `updated_at` | audit |

Table `store_schedules`:

| Field | Dùng cho UI |
| --- | --- |
| `day_of_week` | 0=Sunday đến 6=Saturday |
| `open_time`, `close_time` | giờ mở/đóng |
| `is_open` | ngày có mở cửa không |

Table `store_closure_days`: `closed_date`, `reason`, `created_by_user_id`.

Table `store_special_hours`: `special_date`, `open_time`, `close_time`, `is_closed`, `reason`.

Table `store_reliability_stats`:

| Field | Metric |
| --- | --- |
| `total_bags_listed` | tổng bag đã đăng |
| `total_bags_sold` | tổng bag đã bán |
| `total_bags_fulfilled` | tổng bag đã pickup thành công |
| `total_bags_no_show` | tổng no-show |
| `fulfillment_rate` | tỷ lệ hoàn tất |
| `warning_count` | số cảnh báo |
| `is_under_review`, `suspended_until` | trạng thái rủi ro |

### 3.3 Merchant Onboarding, Business Profile, Documents, Bank

Table `merchant_business_profiles`:

| Field | Dùng cho UI |
| --- | --- |
| `id`, `owner_user_id` | định danh business owner |
| `legal_type` | `INDIVIDUAL`, `HOUSEHOLD_BUSINESS`, `COMPANY`, `COMPANY_BRANCH` |
| `legal_name` | tên pháp lý |
| `representative_full_name`, `representative_phone`, `representative_email` | người đại diện |
| `identity_document_type`, `identity_document_number` | giấy tờ định danh, UI response đang mask số |
| `tax_code`, `registration_number` | mã số thuế/đăng ký |
| `parent_company_name`, `parent_company_tax_code` | công ty mẹ nếu branch |
| `business_address` | địa chỉ kinh doanh |
| `review_status` | `DRAFT`, `PENDING_REVIEW`, `CHANGES_REQUESTED`, `APPROVED`, `REJECTED` |
| `rejection_reason`, `approved_at`, `created_at`, `updated_at` | review/audit |

Table `merchant_documents`:

| Field | Dùng cho UI |
| --- | --- |
| `document_type` | `BUSINESS_LICENSE`, `REPRESENTATIVE_ID`, `AUTHORIZATION_LETTER`, `BANK_PROOF`, `FOOD_SAFETY_CERTIFICATE` |
| `media_upload_id` | file private |
| `review_status` | `PENDING_REVIEW`, `APPROVED`, `REJECTED` |
| `expires_at` | ngày hết hạn nếu có |
| `rejection_reason` | lý do từ chối |

Table `merchant_bank_accounts`:

| Field | Dùng cho UI |
| --- | --- |
| `id`, `business_profile_id`, `store_id` | định danh |
| `bank_code`, `bank_name` | ngân hàng |
| `account_holder_name` | chủ tài khoản |
| `account_number_last4` | chỉ hiển thị dạng mask |
| `is_default` | tài khoản mặc định |
| `verification_status` | `PENDING_REVIEW`, `APPROVED`, `REJECTED` |
| `rejection_reason`, `created_at`, `updated_at` | review/audit |

Table `store_review_applications`: `store_id`, `business_profile_version_id`, `store_version_id`, `status`, `submitted_by`, `submitted_at`, `reviewed_by`, `reviewed_at`, `decision_note`.

Table `review_feedback_items`: `section`, `field_path`, `message`, `resolved`, `resolved_at`, `created_by`.

### 3.4 Surprise Bags, Pricing, Inventory

Table `bag_price_tiers`:

| Field | Dùng cho UI |
| --- | --- |
| `category`, `bag_size` | loại cửa hàng + size |
| `minimum_value` | giá trị tối thiểu của túi |
| `base_sale_price` | giá bán nền |
| `dynamic_min_price`, `dynamic_max_price` | biên dynamic pricing |
| `platform_fee` | phí nền tảng |
| `active` | tier đang dùng hay tắt |

Table `surprise_bags`:

| Field | Dùng cho UI |
| --- | --- |
| `id`, `store_id` | định danh |
| `name`, `description` | tên/mô tả bag |
| `bag_type` | `STANDARD`, `BREAD`, `MEAL`, `GROCERY`, `MIXED` |
| `diet_type` | `MEAT`, `VEGETARIAN`, `VEGAN` |
| `category`, `bag_size` | category/size |
| `photos` | ảnh |
| `minimum_value`, `base_sale_price` | giá trị gốc/giá bán |
| `dynamic_min_price`, `dynamic_max_price`, `dynamic_pricing_enabled` | dynamic pricing |
| `platform_fee` | phí nền tảng |
| `max_per_order` | tối đa mỗi order |
| `container_provided`, `carrier_bag_provided`, `packaging_note` | packaging |
| `pickup_start_time`, `pickup_end_time` | khung pickup |
| `available_days` | ngày bán trong tuần |
| `status` | `DRAFT`, `ACTIVE`, `PAUSED`, `ARCHIVED` |
| `version`, `created_at`, `updated_at` | audit/version |

Table `bag_daily_stocks`:

| Field | Dùng cho UI |
| --- | --- |
| `bag_id`, `store_id`, `date` | tồn kho theo ngày |
| `quantity` | tổng số lượng đăng |
| `reserved` | đang giữ trong đơn pending payment |
| `sold` | đã bán |
| `status` | `ACTIVE`, `SOLD_OUT`, `CANCELLED`, `EXPIRED` |
| `version` | optimistic/concurrency |

Table `stock_audit_logs`: `action`, `delta`, `quantity_before`, `quantity_after`, `reason`, `actor_id`, `order_id`, `created_at`.

### 3.5 Orders, Payment, Pickup

Table `orders`:

| Field | Dùng cho UI |
| --- | --- |
| `id`, `order_number` | định danh đơn |
| `user_id`, `store_id`, `bag_id`, `daily_stock_id` | liên kết customer/store/bag/stock |
| `quantity`, `unit_price`, `platform_fee`, `subtotal`, `discount_amount`, `final_amount` | tiền |
| `status` | `PENDING_PAYMENT`, `PAID`, `READY_FOR_PICKUP`, `PICKED_UP`, `EXPIRED`, `CANCELLED`, `REFUNDED` |
| `pickup_code` | code cho customer/merchant; production UI nên cẩn thận exposure |
| `pickup_date`, `pickup_start_time`, `pickup_end_time` | khung nhận |
| `reserved_until`, `payment_expires_at`, `paid_at`, `picked_up_at`, `cancelled_at`, `expired_at` | timeline |
| `refund_status` | `NONE`, `REQUESTED`, `APPROVED`, `REJECTED`, `REFUNDED`, `PARTIALLY_REFUNDED` |
| `created_at`, `updated_at` | audit |

Table `payments`:

| Field | Dùng cho UI |
| --- | --- |
| `order_id`, `user_id` | liên kết |
| `provider` | `PAYOS`, `FAKE` |
| `provider_order_code`, `provider_payment_link_id` | mã provider |
| `amount`, `currency` | tiền |
| `status` | `PENDING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`, `REFUNDED`, `PARTIALLY_REFUNDED` |
| `checkout_url`, `qr_code` | checkout |
| `expires_at`, `paid_at`, `cancelled_at`, `failure_reason` | trạng thái |
| `raw_provider_payload` | debug/admin only |

Table `payment_transactions`: `provider_transaction_id`, `amount`, `status`, `provider_code`, `provider_description`, `paid_at`.

Table `payment_webhooks`: `event_key`, `provider_order_code`, `signature`, `valid_signature`, `processed`, `failure_reason`.

Table `pickup_events`: `order_id`, `store_id`, `actor_user_id`, `event_type`, `channel`, `notes`, `created_at`.

Table `order_status_history`: `from_status`, `to_status`, `actor_user_id`, `actor_type`, `reason`, `metadata`, `created_at`.

### 3.6 Refund, Review, Settlement, Audit

Table `refund_requests`:

| Field | Dùng cho UI |
| --- | --- |
| `order_id`, `payment_id`, `requested_by_user_id` | liên kết |
| `reason` | `STORE_CANCELLED`, `STORE_NO_STOCK`, `PAYMENT_AFTER_EXPIRY`, `QUALITY_ISSUE`, `ALLERGEN_OR_LABELING`, `QUANTITY_SHORTAGE`, `PLATFORM_ERROR`, `CUSTOMER_COMPLAINT`, `OTHER` |
| `status` | `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `PROCESSING`, `REFUNDED`, `FAILED`, `CANCELLED` |
| `requested_amount`, `approved_amount` | tiền hoàn |
| `description`, `decision_note` | mô tả/quyết định |
| `reviewed_by`, `reviewed_at` | admin review |
| `auto_created` | refund tự động |
| `refund_bank_code`, `refund_bank_name`, `refund_account_holder_name`, `refund_account_number_last4` | bank destination |

Table `refund_transactions`: `refund_request_id`, `provider`, `provider_reference`, `amount`, `status`, `method`, `failure_reason`, `attempt_count`, `processed_at`.

Table `reviews`: `order_id`, `user_id`, `store_id`, `bag_id`, `overall_rating`, `collection_rating`, `quality_rating`, `variety_rating`, `quantity_rating`, `comment`, `visible`, `hidden_reason`, `created_at`.

Table `review_reports`: `review_id`, `reported_by_user_id`, `reason`, `status`, `resolution_note`, `resolved_by`, `resolved_at`.

Table `store_rating_summaries`: `review_count`, `recent_review_count`, `overall_rating_avg`, `collection_rating_avg`, `quality_rating_avg`, `variety_rating_avg`, `quantity_rating_avg`.

Table `ledger_accounts`: `owner_type`, `owner_id`, `account_type`, `currency`, `balance`.

Table `ledger_entries`: `account_id`, `order_id`, `payment_id`, `refund_request_id`, `settlement_id`, `entry_type`, `entry_direction`, `amount`, `available_at`, `description`.

Table `platform_commissions`: `order_id`, `payment_id`, `gross_amount`, `platform_fee_amount`, `merchant_net_amount`, `rate_bps`, `status`.

Table `merchant_settlements`: `business_profile_id`, `store_id`, `period_start`, `period_end`, `gross_amount`, `commission_amount`, `refund_amount`, `net_amount`, `status`, `approved_by`, `approved_at`, `paid_at`.

Table `store_payouts`: `settlement_id`, `store_id`, `bank_account_id`, `provider`, `amount`, `status`, `provider_payout_id`, `provider_transaction_id`, `failure_reason`, `requested_at`, `paid_at`.

Table `admin_audit_logs`: `actor_user_id`, `action`, `target_type`, `target_id`, `ip_address`, `user_agent`, `reason`, `metadata`, `created_at`.

### 3.7 Notifications

Table `notifications`:

| Field | Dùng cho UI |
| --- | --- |
| `recipient_id` | người nhận |
| `type`, `category` | loại/category |
| `title`, `body`, `image_url`, `deep_link` | nội dung |
| `reference_type`, `reference_id`, `payload` | deep link context |
| `is_read`, `read_at`, `created_at` | inbox |

Table `notification_devices`: `user_id`, `device_token`, `device_type`, `app_version`, `is_active`, `last_seen_at`.

Table `notification_deliveries`: `notification_id`, `device_id`, `channel`, `status`, `provider_message_id`, `error_message`, `retry_count`, `sent_at`.

Table `notification_preferences`: `user_id`, `category`, `push_enabled`, `email_enabled`.

## 4. Merchant Portal / Partner Dashboard

### 4.1 Sidebar MVP

MVP nên có:

1. Dashboard
2. Stores
3. Food Bags / Surprise Bags
4. Orders / Pickup
5. Revenue / Payouts
6. Reviews
7. Staff Management
8. Store Settings

Đề xuất nhưng backend chưa đủ riêng: Promotions, Help/Support ticket.

### 4.2 Merchant Dashboard

Mục tiêu: merchant biết hôm nay cửa hàng vận hành ra sao.

Cards nên hiển thị:

| Card | Cách tính từ dữ liệu hiện có |
| --- | --- |
| Today's Revenue | sum `orders.final_amount` với `status` paid/picked up trong ngày hoặc từ payment succeeded |
| Bags Listed Today | sum `bag_daily_stocks.quantity` theo `store_id`, `date=today` |
| Bags Sold | sum `bag_daily_stocks.sold` |
| Bags Remaining | `quantity - reserved - sold` |
| Orders Waiting for Pickup | count orders `PAID`/`READY_FOR_PICKUP` theo pickup date |
| Orders Collected | count orders `PICKED_UP` |
| Cancelled Orders | count orders `CANCELLED` |
| Refund Requests | count `refund_requests` theo store qua order |
| Store Rating | `stores.avg_rating` hoặc `store_rating_summaries.overall_rating_avg` |
| Sell-through Rate | `sold / quantity` |

Gợi ý UI:

- Store selector nếu merchant có nhiều store.
- Date selector: today, yesterday, 7 days, custom.
- Quick list: upcoming pickup, orders delayed, stock sold out, refunds pending.

Backend hiện chưa có endpoint aggregate dashboard riêng. Có thể thêm:

```http
GET /api/v1/merchant/stores/{storeId}/dashboard?date=2026-06-17
```

### 4.3 Stores

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/merchant/stores` |
| `POST` | `/api/v1/merchant/stores` |
| `GET` | `/api/v1/merchant/stores/{storeId}` |
| `PATCH` | `/api/v1/merchant/stores/{storeId}` |
| `PUT` | `/api/v1/merchant/stores/{storeId}/schedules` |
| `POST` | `/api/v1/merchant/stores/{storeId}/submit-review` |
| `PATCH` | `/api/v1/merchant/stores/{storeId}/pause` |
| `PATCH` | `/api/v1/merchant/stores/{storeId}/activate` |

Field form tạo store (`CreateStoreRequest`):

| Field | UI input |
| --- | --- |
| `name` | text, required |
| `description` | textarea |
| `category` | select category |
| `phone` | text |
| `email` | email |
| `address` | textarea/text |
| `district` | text/select |
| `city` | text/select, default Ho Chi Minh |
| `lat`, `lng` | map picker |
| `pickupInstructions` | textarea |
| `coverImageUrl`, `logoUrl` | nên dùng media upload flow thay vì nhập URL tay |
| `businessLicenseNumber`, `businessLicenseImageUrl` | legacy field; flow mới nên dùng merchant document upload |

Field hiển thị store detail:

- name, slug, category, status, verification status.
- address, district, city, lat/lng map pin.
- phone, email.
- pickup instructions.
- cover/logo/storefront/menu/gallery.
- avg rating, total ratings.
- rejection reason nếu rejected/changes requested.
- schedules, closure days, special hours.

Action UI:

- Create store.
- Edit store.
- Upload images.
- Edit weekly schedule.
- Add closure day.
- Add special hours.
- Submit review.
- Pause/activate store.

Lưu ý: các field nhạy cảm sau khi store đã verified như name/category/address/location/images/license có thể tạo pending version chờ admin duyệt, không apply trực tiếp.

### 4.4 Merchant Onboarding

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `POST` | `/api/v1/merchant/business-profile` |
| `GET` | `/api/v1/merchant/business-profile` |
| `PUT` | `/api/v1/merchant/business-profile` |
| `GET` | `/api/v1/merchant/bank-accounts` |
| `POST` | `/api/v1/merchant/bank-accounts` |
| `POST` | `/api/v1/media/uploads/presigned-url` |
| `POST` | `/api/v1/media/uploads/confirm` |

Business profile form:

- legal type.
- legal name.
- representative full name, phone, email.
- identity document type, identity document number.
- tax code.
- registration number.
- parent company name/tax code nếu company branch.
- business address.

Document upload:

- Representative ID: luôn cần.
- Bank proof: luôn cần.
- Business license: cần nếu không phải individual.
- Authorization letter: cần nếu company branch.
- Food safety certificate: nếu muốn xét kỹ ngành food.

Bank account form:

- bank code.
- bank name.
- account holder name.
- account number.
- default account.
- store id optional nếu bank account gắn cho chi nhánh.

### 4.5 Food Bags / Surprise Bags

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `POST` | `/api/v1/merchant/bags` |
| `GET` | `/api/v1/merchant/bags?page=&size=` |
| `PATCH` | `/api/v1/merchant/bags/{bagId}` |
| `DELETE` | `/api/v1/merchant/bags/{bagId}` |
| `PATCH` | `/api/v1/merchant/bags/{bagId}/pause` |
| `PATCH` | `/api/v1/merchant/bags/{bagId}/resume` |
| `PUT` | `/api/v1/merchant/bags/{bagId}/stock/{date}` |
| `PATCH` | `/api/v1/merchant/bags/{bagId}/stock/today` |
| `GET` | `/api/v1/merchant/bags/{bagId}/audit-logs` |

Form tạo/sửa bag nên có:

| Field | UI input |
| --- | --- |
| `storeId` | select store |
| `name` | text |
| `description` | textarea |
| `bagType` | select `STANDARD/BREAD/MEAL/GROCERY/MIXED` |
| `dietType` | select `MEAT/VEGETARIAN/VEGAN` |
| `category` | select |
| `bagSize` | select `MINI/SMALL/STANDARD/LARGE` |
| `photos` | upload/list image |
| `minimumValue` | money input |
| `baseSalePrice` | money input |
| `dynamicMinPrice`, `dynamicMaxPrice` | money input |
| `dynamicPricingEnabled` | toggle |
| `platformFee` | readonly nếu lấy từ price tier |
| `maxPerOrder` | number 1..3 |
| `containerProvided`, `carrierBagProvided` | toggles |
| `packagingNote` | text |
| `pickupStartTime`, `pickupEndTime` | time picker |
| `availableDays` | weekday selector |
| `status` | draft/active/paused/archive state |

List table nên có:

- bag name, store, category, size, diet, type.
- current sale price, minimum value, discount percent.
- pickup window, available days.
- today quantity, reserved, sold, remaining.
- status.
- actions: edit, pause/resume, archive, set stock, view audit log.

### 4.6 Orders / Pickup

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/merchant/stores/{storeId}/orders?date=&status=&page=&size=` |
| `GET` | `/api/v1/merchant/stores/{storeId}/orders/{orderId}` |
| `POST` | `/api/v1/merchant/stores/{storeId}/orders/{orderId}/ready` |
| `POST` | `/api/v1/merchant/stores/{storeId}/orders/{orderId}/cancel` |
| `GET` | `/api/v1/merchant/stores/{storeId}/orders/{orderId}/timeline` |
| `POST` | `/api/v1/merchant/pickups/confirm` |

List filters:

- store.
- pickup date.
- order status.
- search by order number/pickup code/customer phone: backend hiện chưa expose search param, nên cần thêm nếu UI cần.

Columns:

- order number.
- customer name/phone.
- bag name.
- quantity.
- final amount.
- payment status.
- order status.
- refund status.
- pickup date/time.
- paid at, picked up at, cancelled at.

Actions:

- mark ready.
- cancel/no stock, yêu cầu reason.
- confirm pickup bằng QR/code.
- view timeline.

Pickup screen riêng cho staff:

- search/scan input.
- upcoming pickup list sorted by pickup time.
- big status badge.
- confirm button.
- reject/error state nếu wrong code/outside window/already picked up.

### 4.7 Revenue / Payouts

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/merchant/settlements` |
| `GET` | `/api/v1/merchant/settlements/payouts` |
| `GET` | `/api/v1/merchant/settlements/payable-balance` |

UI cards:

- payable balance.
- gross sales this period.
- platform commission.
- refund deduction.
- net payout.
- pending payout.
- paid payout.

Settlement table:

- period start/end.
- gross amount.
- commission amount.
- refund amount.
- net amount.
- status.
- approved at.
- paid at.

Payout table:

- payout id.
- settlement id.
- store.
- bank account masked.
- provider.
- amount.
- status.
- provider payout id/transaction id.
- requested at.
- paid at.
- failure reason.

### 4.8 Reviews

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/stores/{storeId}/reviews` |
| `GET` | `/api/v1/stores/{storeId}/rating-summary` |

Merchant review UI:

- overall rating average.
- collection, quality, variety, quantity averages.
- recent review count.
- review list: rating, comment, photos, created at.
- low rating filter.

Backend chưa có merchant-specific review management endpoint, nhưng public review endpoint có thể dùng read-only.

### 4.9 Staff Management

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `POST` | `/api/v1/merchant/stores/{storeId}/members` |
| `GET` | `/api/v1/merchant/stores/{storeId}/members` |
| `PATCH` | `/api/v1/merchant/stores/{storeId}/members/{memberId}/suspend` |
| `GET` | `/api/v1/store-workspace/me` |

Create member form:

- username.
- full name.
- phone.
- role: `MANAGER` hoặc `STAFF`.

List columns:

- username.
- full name.
- phone.
- role.
- status.
- must change password.
- temporary password chỉ show một lần ngay sau create.
- joined at.

Quyền đề xuất:

| Role | Nên làm được |
| --- | --- |
| MERCHANT_OWNER | tất cả store, bank, payout, staff |
| MANAGER | bag, stock, order, pickup, staff scoped |
| STAFF | order list và confirm pickup |

## 5. Admin Console / Back Office

### 5.1 Sidebar MVP

MVP nên có:

1. Dashboard
2. Merchant Applications
3. Stores
4. Food Bags / Price Tiers
5. Orders
6. Payments
7. Refunds
8. Payouts / Settlements
9. Bank Accounts
10. Reviews & Reports
11. Notifications
12. Customers
13. Audit Logs
14. Analytics
15. Settings

Đề xuất nhưng backend chưa có: Vouchers/Promotions, Support Tickets, Content Management, Admin Users & Roles chi tiết.

### 5.2 Admin Dashboard

Cards nên có:

| Card | Nguồn dữ liệu |
| --- | --- |
| GMV | sum `orders.final_amount` hoặc `payments.amount` succeeded |
| Platform Revenue | sum `platform_commissions.platform_fee_amount` status earned |
| Total Orders | count `orders` |
| Completed Orders | count `orders.status=PICKED_UP` |
| Cancelled Orders | count `CANCELLED` |
| Refund Rate | refunds / paid or completed orders |
| Active Customers | users role customer active |
| Active Merchants | users role merchant active |
| Active Stores | stores status active + verified |
| Bags Listed | sum stock quantity or count active bags |
| Bags Sold | sum `bag_daily_stocks.sold` |
| Sell-through Rate | sold / quantity |
| Average Rating | avg store rating |

Backend cần thêm endpoint aggregate riêng để dashboard chạy nhanh:

```http
GET /api/v1/admin/dashboard?from=&to=
```

### 5.3 Merchant Applications / Store Review

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/admin/stores?verificationStatus=&page=&size=` |
| `GET` | `/api/v1/admin/stores/{storeId}` |
| `PATCH` | `/api/v1/admin/stores/{storeId}/approve` |
| `PATCH` | `/api/v1/admin/stores/{storeId}/reject` |
| `PATCH` | `/api/v1/admin/stores/{storeId}/request-changes` |

List columns:

- store name.
- owner/business profile.
- category.
- address/district/city.
- store status.
- verification status.
- submitted at/created at.
- rating nếu có.

Detail sections:

- store profile.
- business profile.
- bank accounts.
- documents.
- pending business profile snapshot.
- pending store snapshot.
- feedback/request changes.

Admin actions:

- approve.
- reject với `rejectionReason`.
- request changes với list `{ section, fieldPath, message }`.
- view document access URL.

### 5.4 Stores

Admin store UI nên có:

- filters: verification status, store status, category, district/city, keyword.
- table: store name, merchant, category, status, verification, rating, total ratings, created at.
- detail: public profile preview, schedules, images, address map, order history, rating, complaint/refund signals.

Backend hiện có admin review list theo `verificationStatus`, chưa có full admin store search/filter theo keyword/category/status riêng. Có public store search, nhưng admin view nên có endpoint riêng nếu cần.

### 5.5 Food Bags / Price Tiers

Endpoint price tier admin hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/admin/bag-price-tiers` |
| `GET` | `/api/v1/admin/bag-price-tiers/{tierId}` |
| `POST` | `/api/v1/admin/bag-price-tiers` |
| `PATCH` | `/api/v1/admin/bag-price-tiers/{tierId}` |
| `PATCH` | `/api/v1/admin/bag-price-tiers/{tierId}/activate` |
| `PATCH` | `/api/v1/admin/bag-price-tiers/{tierId}/deactivate` |

Price tier fields:

- category.
- bag size.
- minimum value.
- base sale price.
- dynamic min/max price.
- platform fee.
- active.

Admin bag moderation đề xuất:

- list all bags by store/category/status.
- view bag performance.
- hide/flag suspicious bag.

Backend hiện chưa có admin all-bag moderation endpoint riêng; chỉ có merchant bag CRUD và public bag discovery.

### 5.6 Orders

Admin Orders UI nên có:

- filters: order status, payment status, refund status, store, customer, pickup date, created date.
- columns: order number, customer, store, bag, quantity, final amount, payment status, order status, refund status, pickup window, created at.
- detail: order, payment, pickup events, status timeline, refund requests.

Backend hiện có customer order API và merchant order API, nhưng chưa thấy admin all-orders endpoint riêng. Nên thêm:

```http
GET /api/v1/admin/orders?status=&paymentStatus=&refundStatus=&storeId=&customerId=&from=&to=
GET /api/v1/admin/orders/{orderId}
```

### 5.7 Payments

Admin Payments UI nên có:

- filters: provider, status, date, order id, provider order code.
- columns: provider, amount, currency, status, order, customer, expires at, paid at, cancelled at, failure reason.
- detail: transactions, webhooks, raw payload, signature status.

Backend có payment tables và webhook endpoint, nhưng chưa có admin payment list API riêng. Nên thêm endpoint read-only cho Back Office.

### 5.8 Refunds

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/admin/refunds?status=&reason=&page=&size=` |
| `POST` | `/api/v1/admin/refunds/{refundId}/review` |
| `POST` | `/api/v1/admin/refunds/{refundId}/retry` |
| `POST` | `/api/v1/admin/refunds/transactions/{transactionId}/mark-succeeded` |
| `POST` | `/api/v1/admin/refunds/transactions/{transactionId}/mark-failed` |

List columns:

- refund id.
- order id.
- requested by.
- reason.
- status.
- requested amount.
- approved amount.
- destination required.
- auto created.
- created at.

Detail:

- description.
- order detail.
- payment.
- refund bank masked.
- transactions.
- decision note.

Actions:

- approve/reject.
- set approved amount.
- retry failed refund.
- manual mark transaction succeeded/failed.

### 5.9 Payouts / Settlements

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `POST` | `/api/v1/admin/settlements/draft-weekly` |
| `GET` | `/api/v1/admin/settlements?status=&page=&size=` |
| `POST` | `/api/v1/admin/settlements/{settlementId}/approve` |
| `POST` | `/api/v1/admin/settlements/{settlementId}/payout` |
| `POST` | `/api/v1/admin/settlements/payouts/{payoutId}/mark-paid` |
| `POST` | `/api/v1/admin/settlements/payouts/{payoutId}/mark-failed` |

Settlement table:

- business profile.
- store.
- period start/end.
- gross amount.
- commission amount.
- refund amount.
- net amount.
- status.
- approved by/at.
- paid at.

Payout detail:

- settlement id.
- store.
- bank account.
- provider.
- amount.
- status.
- provider payout id/transaction id.
- failure reason.
- requested at.
- paid at.

Actions:

- create weekly drafts.
- approve settlement.
- start payout.
- mark paid.
- mark failed.
- retry by calling payout again if failed.

### 5.10 Bank Accounts

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/admin/bank-accounts?status=&businessProfileId=&storeId=&page=&size=` |
| `GET` | `/api/v1/admin/bank-accounts/{bankAccountId}` |
| `PATCH` | `/api/v1/admin/bank-accounts/{bankAccountId}/approve` |
| `PATCH` | `/api/v1/admin/bank-accounts/{bankAccountId}/reject` |

Columns:

- business profile.
- store.
- bank code/name.
- account holder.
- masked account number.
- default account.
- verification status.
- rejection reason.
- created at.

Actions:

- approve.
- reject với reason.

### 5.11 Reviews & Reports

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/admin/reviews/reports?status=&page=&size=` |
| `POST` | `/api/v1/admin/reviews/reports/{reportId}/resolve` |
| `POST` | `/api/v1/admin/reviews/{reviewId}/hide` |

UI:

- report list by status.
- review detail.
- reporter.
- reason.
- resolution note.
- hide review option.
- store rating impact.

Quality metrics nên có:

- stores with low rating.
- high refund rate stores.
- high cancellation/no-stock stores.
- high no-show rate.
- complaint trend.

### 5.12 Notifications

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `POST` | `/api/v1/notifications/admin/broadcast` |
| `GET` | `/api/v1/notifications/admin/stats` |

Admin notification UI:

- broadcast title/body.
- category/type.
- deep link/reference.
- target hiện tại: active customers theo service hiện có.
- active device token count.
- FCM available status.

Đề xuất thêm sau:

- target theo location, favorite store, order history, dietary preference, inactive users.
- schedule notification.
- delivery analytics.

### 5.13 Customers

Backend có `users`, `orders`, `favorite_stores`, `user_addresses`, `user_discovery_preferences`, nhưng chưa có admin customer management API riêng.

UI nên có:

- customer list.
- profile detail.
- order history.
- refund history.
- favorite stores.
- discovery preference.
- status/email verified/last login.
- action ban/unban nếu backend bổ sung.

### 5.14 Audit Logs

Endpoint hiện có:

| Method | Endpoint |
| --- | --- |
| `GET` | `/api/v1/admin/audit-logs?actorId=&action=&targetType=&targetId=&from=&to=&page=&size=` |

Columns:

- actor name/id.
- action.
- target type.
- target id.
- reason.
- metadata.
- created at.

Filters:

- actor.
- action.
- target type.
- target id.
- date range.

## 6. Analytics / BI Dashboard

Analytics hiện nên xem là read-only module. Backend có dữ liệu gốc, nhưng nên làm API aggregate/export riêng nếu muốn UI nhanh và sạch.

### 6.1 Analytics MVP Menu

1. Overview
2. Sales
3. Orders
4. Customers
5. Merchants / Stores
6. Bags
7. Refunds
8. Reviews
9. Finance
10. Export Reports

### 6.2 Marketplace Overview

Metric:

| Metric | Công thức/Nguồn |
| --- | --- |
| GMV | sum successful `payments.amount` hoặc `orders.final_amount` paid/completed |
| Net Revenue | sum `platform_commissions.platform_fee_amount` earned |
| Orders | count `orders` |
| Completed Orders | count `orders.status=PICKED_UP` |
| Average Order Value | GMV / successful orders |
| Refund Amount | sum approved/refunded `refund_requests.approved_amount` |
| Active Customers | count users/order activity by period |
| Active Merchants | count merchant owners/stores active by period |
| Active Stores | count stores `ACTIVE` + `VERIFIED` |
| Bags Sold | sum `bag_daily_stocks.sold` |
| Sell-through Rate | sold / quantity |

### 6.3 Funnel

Funnel đề xuất:

```text
App open
-> Home viewed
-> Bag viewed
-> Bag reserved/order created
-> Checkout started
-> Payment success
-> Pickup completed
-> Rating submitted
```

Backend hiện có dữ liệu từ `order created` trở đi và đã có `store_engagement_events` cho store/bag view, store/bag card click. Các bước rộng hơn như app open, home viewed, search keyword vẫn cần event tracking bổ sung nếu muốn funnel đầy đủ toàn app.

Metrics có thể tính hiện tại:

- bag reserved: count orders created.
- checkout started: payment row created.
- payment success: payment status succeeded.
- pickup completed: order status picked up.
- rating submitted: count reviews.

### 6.4 Customer Analytics

Có thể xem:

- new users by day.
- returning users theo orders.
- average orders per user.
- favorite stores.
- preferred diet.
- preferred collection times.
- default radius/location nếu user lưu preference.
- refund rate by customer.

Cần thêm tracking nếu muốn:

- DAU/WAU/MAU app opens chuẩn.
- retention D1/D7/D30 chuẩn.
- search keyword analytics.
- bag view conversion.

### 6.5 Merchant / Store Analytics

Metric:

- active stores.
- stores by category/district/city.
- bags posted per store.
- sell-through rate per store.
- revenue per store.
- cancellation rate.
- refund rate.
- fulfillment rate.
- no-show rate.
- average rating.
- top performing stores.
- stores under review/warning.

Nguồn:

- `stores`.
- `surprise_bags`.
- `bag_daily_stocks`.
- `orders`.
- `refund_requests`.
- `store_reliability_stats`.
- `store_rating_summaries`.

### 6.6 Bag Analytics

Metric:

- best-selling category.
- best pickup time.
- average discount: `(minimum_value - base_sale_price) / minimum_value`.
- average sold quantity.
- unsold quantity.
- sold out rate.
- time-to-sell-out: cần event/history chi tiết hơn nếu muốn chính xác.
- conversion view-to-order: có thể dùng `store_engagement_events` cho bag view/card click kết hợp order/payment.

### 6.7 Geography Analytics

Metric:

- stores by district/city.
- orders by district/city qua store.
- GMV by district.
- supply heatmap: stores/bags quantity by location.
- demand heatmap: orders/users by location.
- areas high demand but low supply.

Hiện backend có store `lat/lng`, city, district và user default location. Chưa có app event heatmap.

### 6.8 Finance Analytics

Metric:

- GMV by day.
- platform fee by day.
- merchant net amount.
- refund amount.
- payout amount.
- failed payment rate.
- payout failed rate.
- settlement status distribution.
- PayOS webhook failures.

Nguồn:

- `payments`.
- `payment_transactions`.
- `payment_webhooks`.
- `platform_commissions`.
- `ledger_entries`.
- `merchant_settlements`.
- `store_payouts`.
- `refund_requests`.
- `refund_transactions`.

### 6.9 Export Reports

Export nên có:

- orders CSV.
- payments CSV.
- refunds CSV.
- settlements/payouts CSV.
- merchant/store performance CSV.
- customer analytics CSV.

Backend hiện chưa có export endpoint riêng.

## 7. Những Module UI Đề Xuất Nhưng Backend Chưa Có Đủ

| Module | Trạng thái hiện tại |
| --- | --- |
| Promotions / Vouchers | Chưa có table/API. `orders.discount_amount` có sẵn nhưng chưa có voucher engine |
| Support Tickets | Chưa có table/API |
| Content Management | Chưa có table/API riêng cho banner, FAQ, policy, notification template |
| Admin Users & Roles nâng cao | Backend có role cơ bản, chưa có permission matrix như support/finance/analyst |
| Analytics aggregate API | Đã có merchant store engagement aggregate; admin/global aggregate/export vẫn cần bổ sung |
| Admin all-orders/payment/customer search | Dữ liệu có trong DB, nhưng chưa thấy endpoint admin riêng đầy đủ |
| Bag moderation cho admin | Dữ liệu có, price tier API có, nhưng chưa có all-bag moderation endpoint |
| FE event tracking | Đã có event table/API cho store/bag view và card click; app open, home viewed, search keyword vẫn cần bổ sung |

## 8. Tóm Gọn Để Đi Làm UI

### Merchant Portal MVP

Làm các màn:

1. Dashboard: today revenue, bags listed/sold/remaining, pickup pending, collected, refunds, rating.
2. Stores: list/detail/create/edit store, upload image, schedule, closure, special hours, submit review, pause/activate.
3. Onboarding: business profile, documents, bank account.
4. Food Bags: create/edit/pause/resume/archive, set stock, stock audit.
5. Orders / Pickup: list by date/status, detail, mark ready, cancel/no-stock, confirm pickup by QR/code, timeline.
6. Revenue: payable balance, settlements, payouts.
7. Reviews: rating summary, review list.
8. Staff: create manager/staff, list, suspend.

### Admin Console MVP

Làm các màn:

1. Dashboard: GMV, revenue, orders, refunds, active users/stores, bags sold, sell-through, rating.
2. Merchant Applications: pending stores, business profile, documents, bank, approve/reject/request changes.
3. Stores: store list/filter/detail.
4. Price Tiers: category/size pricing CRUD.
5. Orders: nên thêm admin endpoint nếu muốn quản lý toàn hệ thống.
6. Payments: nên thêm admin endpoint nếu muốn xem PayOS/payment detail.
7. Refunds: list, review, retry, manual mark succeeded/failed.
8. Settlements/Payouts: draft weekly, approve, payout, mark paid/failed.
9. Bank Accounts: list/detail/approve/reject.
10. Reviews & Reports: reports, resolve, hide review.
11. Notifications: broadcast, FCM stats.
12. Customers: cần thêm admin customer endpoint.
13. Audit Logs: search/filter audit.

### Analytics / BI MVP

Làm các màn:

1. Overview: GMV, net revenue, orders, AOV, refunds, active customers/merchants/stores.
2. Sales: GMV/platform fee by day, category, store, district.
3. Orders: status distribution, pickup completion, cancellation/no-show.
4. Customers: new/returning, orders per user, preferences.
5. Merchants: active stores, revenue, sell-through, refund/cancel rate, rating.
6. Bags: sold by category, pickup time, unsold, discount.
7. Refunds: reason/status trend, amount, failed transactions.
8. Finance: payout, settlement, ledger, failed payment/payout.
9. Export Reports: CSV export.

## 9. Ưu Tiên Backend Cần Thêm Để UI Mượt

1. `GET /api/v1/merchant/stores/{storeId}/dashboard`
2. `GET /api/v1/admin/dashboard`
3. `GET /api/v1/admin/orders`
4. `GET /api/v1/admin/payments`
5. `GET /api/v1/admin/customers`
6. `GET /api/v1/admin/bags`
7. `GET /api/v1/admin/analytics/*`
8. `GET /api/v1/admin/export/*`
9. Voucher/campaign tables + API nếu muốn có Promotions.
10. Support ticket tables + API nếu muốn có Support Dashboard.

## 10. Kết Luận

Cách chia **Merchant Portal**, **Admin Console/Back Office** và **Analytics/BI Dashboard** là đúng. Với backend hiện tại, Merchant Portal và Admin Console đã có nền dữ liệu/API khá mạnh để làm MVP. Analytics có thể làm được phần tổng hợp từ dữ liệu gốc, nhưng để chuyên nghiệp và dễ mở rộng thì nên bổ sung các endpoint aggregate/export riêng.

Nếu cầm tài liệu này đi làm UI, nên bắt đầu từ Merchant Portal và Admin Console MVP trước, sau đó thêm Analytics aggregate API để dashboard không phải tự tính quá nhiều ở frontend.
