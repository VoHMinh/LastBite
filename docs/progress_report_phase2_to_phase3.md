# LastBite Backend Progress Report

Báo cáo cập nhật ngày: 2026-06-01  
Phạm vi: Backend API cho LastBite Food Rescue Platform  
Trạng thái tổng thể: Phase 2 gần hoàn tất, sẵn sàng chuẩn bị vào Phase 3

## 1. Executive Summary

| Hạng mục | Trạng thái | Đánh giá | Ghi chú điều hành |
|---|---:|---|---|
| Phase 1 - Authentication, user base, store foundation | Done | Green | Nền tảng đăng nhập, refresh token, Google auth, email verification, user profile, address, store owner/public store đã có. |
| Phase 2 - Bag inventory, discovery, pricing, order reservation | Mostly Done | Green | Core business flow đã hoạt động: merchant tạo bag, quản lý stock theo ngày, public discovery, dynamic pricing, order reservation, audit log. |
| Phase 2 polish - Customer personalization & bag detail UX | In Progress | Yellow/Green | Đang bổ sung diet filter, favorite store, store rating/media, packaging detail vào public bag APIs. |
| Phase 3 readiness | Ready to Start | Yellow | Nền tảng kỹ thuật đủ tốt để đi vào payment/order lifecycle/admin moderation, nhưng cần chốt API contract và integration QA trước khi đóng Phase 2. |
| Quality gate | Passed | Green | `mvn test` đã pass: 30 tests, 0 failures, build success. |

## 2. Scope Completion By Module

| Module | Mục tiêu sản phẩm | Kết quả đã hoàn thành | Trạng thái | Bằng chứng trong codebase |
|---|---|---|---|---|
| Auth & Security | Cho phép user đăng ký/đăng nhập an toàn | Customer/partner registration, login, refresh token, logout, logout all, Google auth, email verification, JWT resource server, role authority fix | Done | `modules/auth`, `SecurityConfig`, migrations V1-V5 |
| User Profile | Quản lý thông tin cá nhân khách hàng | Get/update profile, change password, multi-address, default address, Redis cache | Done | `modules/user`, `UserController`, `AddressService` |
| Store Owner | Cho merchant tạo và quản lý cửa hàng | Create/update store, schedules, pause/activate store, auto promote STORE_OWNER, slug generation | Done | `modules/store`, `StoreOwnerController`, migration V3 |
| Public Store | Cho khách tìm và xem cửa hàng | Search stores, get store by slug, cache list/detail, avoid N+1 via entity graph | Done | `StorePublicController`, `StoreQueryService` |
| Admin Pricing | Admin quản lý bảng giá platform | CRUD price tiers, activate/deactivate, default seed price tiers theo category/size | Done | `AdminBagPriceTierController`, `BagPricingService`, migration V6 |
| Merchant Bags | Merchant tạo và quản lý surprise bag | Create/update/list/delete bag, pause/resume, set stock, adjust today stock, audit logs | Done | `MerchantBagController`, `SurpriseBagService`, `StockAuditLog` |
| Bag Discovery | Khách xem túi hôm nay/gần đây | Today bags, nearby bags, filter theo location/category/district/diet/bag type, sort, limit, sold-out handling | Mostly Done | `BagPublicController`, `BagDiscoveryService`, `BagDailyStockRepository` |
| Dynamic Pricing | Giá thay đổi theo thời gian/tồn kho | Price snapshot, min/base/max price, current price calculation, platform fee | Done | `BagPricingService`, `BagPricingServiceTest` |
| Order Reservation | Giữ hàng khi khách đặt order | Create order, idempotency key, pessimistic stock lock, reserved quantity, 10-minute reservation TTL, pickup code | Done for MVP | `OrderService`, `OrderController`, `OrderServiceTest` |
| Favorite Stores | Cá nhân hóa trải nghiệm khách hàng | Favorite store repository/service/test, favorite flag trên public bag response đang được tích hợp | In Progress | `FavoriteStoreService`, `FavoriteStoreRepository`, V7 |
| Bag Packaging Detail | Hiển thị thông tin bao bì khi mua túi | Thêm container/carrier bag flags và packaging note vào entity/request/response/public detail | In Progress | V8, `SurpriseBag`, bag DTOs |
| API Collection | Hỗ trợ QA/manual testing | Bruno collection cho Auth, User, StoreOwner, MerchantBags, Public, Admin, Orders | In Progress | `LastBite-API` |

## 3. Technical Delivery Metrics

| Chỉ số | Hiện tại | Ý nghĩa |
|---|---:|---|
| Test suite | 30 tests passing | Các service quan trọng đã có unit/integration-style coverage. |
| Build status | Success | Project compile và test thành công với Maven. |
| Core classes analyzed by JaCoCo | 68 classes | Backend đã có độ lớn rõ ràng cho MVP. |
| Database migrations | V1 -> V8 | Schema đã tiến hóa theo từng phase, có Flyway versioning. |
| Public/merchant/admin API groups | 8 groups | Auth, User, StoreOwner, Public Store, Public Bags, Merchant Bags, Admin Pricing, Orders. |
| Caching | Redis cache-aside | User profile/address, store list/detail, bag discovery/detail. |
| Concurrency control | Pessimistic stock lock | Giảm rủi ro oversell khi nhiều user đặt cùng một bag. |

## 4. Phase 2 Delivery Detail

| Area | Đã làm được | Giá trị kinh doanh | Mức độ sẵn sàng |
|---|---|---|---|
| Surprise Bag catalog | Merchant tạo bag theo category, size, type, pickup window, available days, pricing snapshot | Tạo sản phẩm chính của LastBite | Production candidate |
| Daily inventory | Stock theo ngày, quantity/reserved/sold/status, giới hạn max stock | Quản lý hàng tồn cuối ngày, tránh bán vượt tồn | Production candidate |
| Discovery | Khách xem bag theo bán kính, district fallback, category, diet, bag type, sort | Tăng khả năng tìm thấy deal gần người dùng | Beta ready |
| Pricing | Bảng giá platform theo category/size, dynamic pricing min/base/max | Đảm bảo giá bán đồng nhất, có biên lợi nhuận/platform fee | Production candidate |
| Reservation | Đặt giữ bag, idempotency key, pickup code, reserved until | Chuyển discovery thành giao dịch thật | MVP ready |
| Audit log | Log stock add/reduce/set/reserve | Truy vết vận hành và dispute | MVP ready |

## 5. Current Work In Progress

| Workstream | Nội dung đang làm | Trạng thái | Việc cần chốt |
|---|---|---|---|
| Diet filter | Thêm `diet_type` cho surprise bags và filter public API | Implemented in schema/service path | Cần verify API contract với frontend. |
| Favorite store flag | Public bag response trả về store có nằm trong favorite của user hay không | Implemented in service diff | Cần tối ưu nếu query favorite bị lặp trên list lớn. |
| Store media/rating on bag cards | Public bag summary/detail có logo, cover image, avg rating, total ratings | Implemented in response mapping | Cần chốt UI fields với frontend. |
| Packaging detail | Thêm `containerProvided`, `carrierBagProvided`, `packagingNote` | Implemented in entity/DTO/migration | Cần bổ sung API collection và docs final. |
| Bruno API collection | Tạo collection để test nhanh các endpoint | Added but chưa commit | Cần clean sample IDs và flow test trước khi share team. |

## 6. Risks, Blockers, And Mitigation

| Risk | Mức độ | Tác động | Đề xuất xử lý |
|---|---|---|---|
| Payment flow chưa có | High | Order mới dừng ở `PENDING_PAYMENT`, chưa có paid/cancel/refund lifecycle | Phase 3 nên ưu tiên payment intent, webhook, order state machine. |
| Admin store verification chưa đầy đủ | Medium | Merchant onboarding chưa có luồng duyệt/reject hoàn chỉnh | Bổ sung admin endpoint duyệt store, rejection reason, audit admin action. |
| Reservation expiry cleanup chưa rõ | Medium | Stock reserved có thể bị treo nếu user không thanh toán | Thêm scheduled job expire reservation và release stock. |
| Cache invalidation cần thận trọng với favorite flag | Medium | Cache discovery theo user nếu thiếu eviction có thể hiện favorite sai | Evict/partition cache theo user hoặc tách favorite overlay. |
| API docs/Bruno collection cần đồng bộ | Medium | QA/frontend có thể test sai contract nếu docs lệch code | Chốt Swagger là source of truth, update collection sau mỗi API change. |
| Encoding/log tiếng Việt hiển thị lỗi trên terminal | Low | Không ảnh hưởng logic nhưng gây khó đọc log/report | Chuẩn hóa file encoding UTF-8 và log message. |

## 7. Recommended Phase 3 Plan

| Priority | Hạng mục Phase 3 | Kết quả mong đợi | Acceptance Criteria |
|---:|---|---|---|
| P0 | Payment & checkout lifecycle | User thanh toán order hoàn chỉnh | Tạo payment, confirm paid, webhook update, release stock khi failed/expired. |
| P0 | Reservation expiry job | Tự động giải phóng stock nếu quá hạn thanh toán | Job quét `PENDING_PAYMENT` quá `reservedUntil`, cập nhật order `EXPIRED`, giảm reserved. |
| P1 | Merchant order management | Store owner xem và xử lý order | List orders by store, mark ready, confirm picked up bằng pickup code. |
| P1 | Admin moderation | Admin duyệt store và kiểm soát pricing | Verify/reject store, manage price tiers, audit admin changes. |
| P1 | Customer order history | Khách xem đơn hàng và trạng thái | List/detail order, pickup info, cancellation policy. |
| P2 | Notification/email | Thông báo email/push cho order/payment/pickup | Email templates, async send, retry/failure logging. |
| P2 | Observability | Sẵn sàng demo/staging ổn định | Health checks, structured logs, key metrics for orders, stock, payment. |

## 8. Management Update Table

| Category | Last Completed | Current Focus | Next Milestone | Status |
|---|---|---|---|---|
| Product backend | Phase 2 core bag/order reservation | Polish discovery UX fields | Close Phase 2 API contract | On Track |
| Customer experience | Public bag search/detail | Favorite, diet, packaging info | Order history + checkout | On Track |
| Merchant experience | Bag/stock management | Packaging and stock visibility | Merchant order operations | On Track |
| Admin operations | Price tier management | Store verification gap analysis | Admin moderation dashboard APIs | Needs Phase 3 |
| Engineering quality | Tests pass, Flyway migrations, cache, locking | API collection cleanup | CI-ready test command and staging smoke test | On Track |

## 9. Final Recommendation

| Decision | Recommendation |
|---|---|
| Phase 2 status | Có thể xem Phase 2 core là hoàn tất về backend logic. Nên dành 1 sprint ngắn để polish API contract, docs và manual QA. |
| Go/No-Go vào Phase 3 | Go, với điều kiện đóng các thay đổi WIP hiện tại và test lại public bag/favorite/packaging endpoints. |
| Phase 3 first sprint | Nên bắt đầu bằng payment lifecycle + reservation expiry, vì đây là nút thắt quan trọng nhất để biến discovery thành revenue flow. |
| Message cho stakeholder | Backend đã có nền tảng sản phẩm chính: merchant tạo túi, khách tìm túi, hệ thống tính giá và giữ stock khi đặt. Phase 3 sẽ tập trung biến reservation thành giao dịch hoàn chỉnh và tăng khả năng vận hành/admin. |
