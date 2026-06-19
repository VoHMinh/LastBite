# Báo Cáo Tiến Độ Tuần 6 - LastBite

**Thời điểm rà soát:** 17/06/2026  
**Dự án:** LastBite Backend  
**Mục tiêu sản phẩm:** Nền tảng food rescue marketplace tương tự Too Good To Go, cho phép cửa hàng bán surplus food theo dạng surprise bag, khách hàng đặt mua, thanh toán, pickup, review/refund và merchant nhận đối soát.

## 1. Tóm Tắt Tuần 6

Đến tuần 6, LastBite đã đi qua mức backend CRUD cơ bản và đã có phần lớn lõi nghiệp vụ của một marketplace kiểu Too Good To Go. Backend hiện bao phủ các luồng chính: xác thực, customer profile, merchant onboarding, store review, surprise bag, tồn kho theo ngày, public discovery, order reservation, PayOS payment, QR/code pickup, refund, review/rating, notification, ledger, settlement và admin operations.

Trạng thái tổng thể: backend core đang ở mức khá hoàn chỉnh cho MVP nâng cao. Phần còn thiếu lớn nhất để có thể xem là một nền tảng hoàn thiện nằm ở voucher/campaign engine, kiểm thử end-to-end môi trường thật, legal/policy wording, dữ liệu demo/seed cho vận hành, observability và tích hợp frontend đầy đủ.

## 2. Căn Cứ Rà Soát

- Đọc luồng nghiệp vụ trong `docs/frontend_business_flows.md`.
- Đối chiếu auth flow trong `docs/auth_frontend_flow.md`.
- Đối chiếu README và roadmap hiện tại.
- Rà nhanh controller, migration, module service/entity/repository trong `src/main/java/com/LastBite/modules`.
- Rà danh sách test trong `src/test/java/com/LastBite`.

Số liệu code hiện tại:

| Hạng mục | Hiện trạng |
| --- | --- |
| Migration DB | 14 migration, từ `V1` đến `V14` |
| Java module files | Khoảng 355 file dưới `modules` |
| Controller | 31 controller |
| Test method | 40 test method được khai báo |
| API documentation | Có `LastBite-API` collection và docs frontend flow |

## 3. Những Phần Đã Hoàn Thành

### 3.1 Auth, Session, Role

- Đăng ký customer và merchant owner.
- Verify email bằng OTP hoặc email link.
- Login email/password, Google login và store member login.
- Refresh token chuyển sang HttpOnly cookie, có rotate session.
- Logout phiên hiện tại và logout toàn bộ thiết bị.
- Phân vai chính: `CUSTOMER`, `MERCHANT_OWNER`, `MANAGER`, `STAFF`, `ADMIN`.
- Store member có luồng đổi mật khẩu bắt buộc lần đầu.

### 3.2 Customer Profile, Discovery Preference, Address, Favorite

- User profile: xem/cập nhật thông tin cá nhân, đổi mật khẩu.
- Address book: thêm/sửa/xóa/set default, giới hạn tối đa 10 địa chỉ.
- Favorite store: thêm/bỏ yêu thích, phục vụ discovery và notification restock.
- Discovery preferences: lưu location mặc định, bán kính, diet preference, preferred collection times và trạng thái onboarding.
- Có luồng skip onboarding để FE không hỏi lại preference khi user bỏ qua.

### 3.3 Media Upload Và Tài Liệu

- Có media upload flow qua S3 pre-signed URL.
- Hỗ trợ public image cho store cover/logo/storefront/menu/gallery/avatar/review photo.
- Hỗ trợ private business document: business license, representative ID, authorization letter, bank proof, food safety certificate.
- Có confirm upload để backend apply side effect đúng target.
- Có signed access URL cho private document.

### 3.4 Merchant Onboarding Và Store Review

- Merchant owner có business profile, bank account, multi-store.
- Store có lịch tuần, closure days, special hours.
- Có submit review, admin approve/reject/request changes.
- Store/business profile có versioning cho thay đổi nhạy cảm sau khi đã verified.
- Có bank account review bởi admin.
- Có store workspace cho manager/staff.
- Có member management: tạo manager/staff, suspend member, temporary password và forced initial password change.

### 3.5 Surprise Bag, Pricing, Inventory

- Admin quản lý bag price tier theo category/size.
- Merchant tạo/cập nhật/pause/resume/archive bag.
- Bag có size, type, diet type, pickup window, packaging detail, availability days.
- Daily stock có set stock, adjust today stock và stock audit log.
- Có job tạo stock mỗi ngày và expire unsold stock cuối ngày.
- Public discovery hỗ trợ today bags, nearby bags, bag detail và store bags.

### 3.6 Order, Payment, Pickup

- Customer tạo order theo reservation-first inventory, tránh oversell.
- Có idempotency khi tạo order.
- Tích hợp PayOS payment link/webhook, backend chỉ đổi trạng thái tiền qua webhook đã verify.
- Có lifecycle: `PENDING_PAYMENT`, `PAID`, `READY_FOR_PICKUP`, `PICKED_UP`, `CANCELLED`, `EXPIRED`.
- Merchant có list order, xem detail, mark ready, cancel/no-stock.
- Pickup xác nhận bằng QR token hoặc manual pickup code.
- Có order timeline cho customer và merchant.
- Có job expire pending payment và no-show pickup.

### 3.7 Refund, Review, Settlement

- Customer tạo refund request/dispute sau order đủ điều kiện.
- Admin review refund, approve/reject, retry và manual mark transaction.
- Có refund destination, mã hóa account number và chỉ expose last4.
- Có refund payout worker qua PayOS payout/manual fallback.
- Customer review order sau pickup, có rating nhiều tiêu chí và review photo.
- Public rating summary cho store.
- User report review, admin resolve report/hide review.
- Có ledger escrow-style: platform cash, escrow, platform revenue, merchant payable, refund liability.
- Có weekly settlement draft, approve, payout, manual mark paid/failed.
- Merchant xem payable balance, settlements và payouts.

### 3.8 Notification, Admin, Audit

- Có in-app notification inbox, unread count, mark read/read all, delete.
- Có FCM device registration, deactivate device, preference theo category.
- Có admin broadcast và FCM stats.
- Notification trigger đã có cho order reserved, payment expiring, pickup reminders, pickup window, pickup ending soon, favorite store restock và broadcast.
- Admin audit log đã expose API search/list.
- Order status history đã ghi nhiều lifecycle event quan trọng.

## 4. Đang Có Nhưng Cần Tích Hợp/Kiểm Thử Kỹ Hơn

| Hạng mục | Trạng thái | Việc cần làm tiếp |
| --- | --- | --- |
| Frontend integration | Backend đã có flow rõ trong `frontend_business_flows.md` | FE cần map đầy đủ route, auth state, role guard, order polling và merchant/admin dashboard |
| PayOS production | Backend có adapter/webhook/payout | Cần smoke test giao dịch thật giá trị nhỏ ở staging/production |
| S3 media | Backend có pre-signed upload | Cần test bucket thật, CORS bucket, private document access và size/content type |
| FCM | Backend có device/inbox/dispatch | Cần test credential thật, browser/mobile token lifecycle và notification permission UX |
| Settlement/refund money flow | Có ledger và payout flow | Cần test kịch bản tiền thật/giả cuối kỳ, failed payout, retry, partial refund |
| Admin operations | Có controller chính | Cần FE admin dashboard hoàn chỉnh và checklist vận hành |
| Documentation/API contract | Docs khá đầy đủ | Nên sync lại README vì một số known gaps trong README đã cũ |

## 5. Những Phần Còn Thiếu Để Hoàn Thiện Như Too Good To Go

### 5.1 Product/Business Features

- Voucher/campaign engine chưa có DB/code/API. Order có `discountAmount` nhưng chưa có flow áp mã giảm giá, campaign hoặc referral.
- Chưa thấy flow campaign theo merchant/platform như flash deal, seasonal campaign, first-order offer.
- Chưa có cơ chế curated collection hoặc ranking nâng cao cho discovery ngoài filter/search hiện tại.
- Chưa có loyalty/referral/customer retention nếu muốn sản phẩm gần hơn với app thương mại thật.

### 5.2 Launch Readiness

- Legal/refund policy wording cần được viết rõ và review theo luật/điều khoản vận hành tại thị trường mục tiêu.
- Cần production smoke test cho PayOS, payout, refund, S3 upload và FCM.
- Cần seed/demo data chuẩn cho store, bag, order, admin review, refund, settlement để demo end-to-end.
- Cần checklist vận hành admin: duyệt store, duyệt bank, xử lý refund, payout failed, review moderation.

### 5.3 Quality, Testing, Observability

- Test hiện có chủ yếu ở service/unit level; nên bổ sung integration test cho auth, order-payment-webhook, pickup, refund, settlement và media.
- Cần E2E test hoặc smoke script chạy qua flow customer đặt túi từ discovery đến payment/pickup/review.
- Cần observability cho production: structured logs, request correlation id, metrics cho payment/refund/payout job, alert khi webhook/payout fail.
- Cần hardening thêm cho security/rate limit ở auth, OTP resend, payment webhook, upload presigned URL và admin endpoints.
- Cần kiểm thử concurrency sâu hơn cho stock reservation và idempotency order dưới tải cao.

## 6. Ghi Chú Kiểm Thử Tuần 6

Repo có 40 test method được khai báo trong các nhóm như admin seeder, bag pricing/discovery/inventory, favorite store, discovery preference, media upload và order service.

Lần chạy `mvnw.cmd test` tại máy hiện tại chưa chạy được do Maven wrapper lỗi:

```text
Cannot start maven from wrapper
```

Máy cũng chưa có Maven global (`mvn` không được nhận diện), nên báo cáo này chưa xác nhận được trạng thái pass/fail của test suite tại thời điểm rà soát. Cần sửa Maven wrapper hoặc cài Maven/Java toolchain chuẩn rồi chạy lại:

```powershell
.\mvnw.cmd test
```

## 7. Đề Xuất Ưu Tiên Tuần 7

1. Hoàn thiện và chạy được test suite local/CI, trước mắt sửa Maven wrapper hoặc chuẩn hóa môi trường chạy test.
2. Sync lại README phần `Current Roadmap / Known Gaps` vì một số gap cũ đã được implement.
3. Làm smoke test end-to-end cho customer flow: register/login, chọn location, discovery, create order, fake/PayOS payment webhook, pickup, review.
4. Làm smoke test merchant/admin flow: onboarding, upload docs/images, submit review, admin approve, create bag, set stock, settlement.
5. Thiết kế voucher/campaign engine tối thiểu: mã giảm giá, campaign theo thời gian, giới hạn lượt dùng, audit và order discount snapshot.
6. Chuẩn bị launch checklist: legal wording, env production, PayOS/S3/FCM credentials, seed data, admin runbook và monitoring.

## 8. Kết Luận

LastBite ở tuần 6 đã có nền backend rất gần với một MVP marketplace thật: có discovery, merchant onboarding, inventory, thanh toán, pickup, refund, review, notification và settlement. Điểm mạnh hiện tại là nghiệp vụ đã được tách module rõ và có nhiều luồng vận hành thực tế hơn một project demo.

Để hoàn thiện thành một nền tảng tương tự Too Good To Go có thể demo/launch tự tin, trọng tâm tiếp theo không chỉ là thêm endpoint mới mà là hoàn thiện tích hợp FE, kiểm thử end-to-end, xử lý production readiness, legal/policy và bổ sung promotion/campaign engine.
