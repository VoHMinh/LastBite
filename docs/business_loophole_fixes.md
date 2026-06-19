# LastBite Business Loophole Fix Report

Tài liệu này ghi lại các lỗ hổng nghiệp vụ đã rà soát trong flow no-show, refund, settlement, reliability và stock audit. Các fix trong batch này giữ policy: **customer no-show không tự động refund**, nhưng customer vẫn có quyền tạo dispute/refund trong 30 ngày nếu lỗi đến từ store/platform.

Nguồn tham chiếu nghiệp vụ:
- Too Good To Go Terms: customer phải pickup trong pickup window; nếu không pickup đúng hạn thì bag có thể không còn available và customer có thể vẫn bị charge; refund/dispute có window sau pickup. Link: https://www.toogoodtogo.com/en-us/legal/terms-and-conditions-using-the-app
- Grab VN Terms: refund/remittance có thể trừ các khoản refund/cost; platform có quyền áp dụng biện pháp với merchant không đạt chuẩn chất lượng/performance. Link: https://www.grab.com/vn/en/terms-policies/transport-delivery-logistics/

## 1. No-show vẫn trả tiền cho merchant

**Kết luận:** Một phần đúng, nhưng đây không hẳn là bug nghiệp vụ.

**Vị trí vấn đề:**
- `PickupService.expireNoShows()` chuyển order `PAID/READY_FOR_PICKUP -> EXPIRED`.
- Hàm này vẫn gọi `ledgerService.recordOrderCompleted(...)`, nên merchant được ghi payable.

**Vì sao không bỏ payable:**
- Với mô hình rescue food giống Too Good To Go, store đã giữ phần ăn tới hết pickup window. Nếu khách không tới, no-show thường không auto-refund.
- Nếu tự refund mọi no-show, merchant chịu thiệt dù đã giữ hàng và không thể bán lại sau giờ pickup.

**Fix đã làm:**
- Giữ merchant payable cho no-show.
- Thêm notification `ORDER_MISSED_PICKUP` cho customer.
- Timeline ghi reason `CUSTOMER_NO_SHOW` và metadata `disputeWindowUntil=...`.
- Reliability chỉ tăng `total_bags_no_show`, không tính merchant fault.

**Ví dụ trước fix:**
- Khách miss pickup, order `EXPIRED`, merchant payable được ghi, nhưng customer không được giải thích rõ có được dispute hay không.

**Ví dụ sau fix:**
- Khách miss pickup lúc 20:15.
- Backend mark `EXPIRED`, gửi notification missed pickup.
- FE đọc timeline thấy `CUSTOMER_NO_SHOW` và `disputeWindowUntil=2026-07-19T...`.
- FE không hiện auto-refund, nhưng hiện CTA report problem nếu store đóng cửa/không có hàng/sai chất lượng.

## 2. No-show thiếu thông báo và dispute clarity

**Kết luận:** Đúng.

**Vị trí vấn đề:**
- `NotificationType` đã có `ORDER_MISSED_PICKUP`, nhưng `NotificationServicePort` và `NotificationService` chưa có method gửi notification này.
- `PickupService.expireNoShows()` ghi timeline metadata null.

**Fix đã làm:**
- Thêm `NotificationServicePort.notifyOrderMissedPickup(Order, Instant disputeWindowUntil)`.
- Implement trong `NotificationService` với payload gồm `disputeWindowUntil`.
- `PickupService.expireNoShows()` gọi notification và ghi timeline metadata.

**Ví dụ sau fix cho FE:**
```json
{
  "type": "ORDER_MISSED_PICKUP",
  "referenceType": "ORDER",
  "payload": {
    "orderId": "...",
    "orderNumber": "LB-12345678",
    "disputeWindowUntil": "2026-07-19T13:15:00Z"
  }
}
```

## 3. Merchant cancel/no-stock paid order chưa bị tính reliability

**Kết luận:** Đúng.

**Vị trí vấn đề:**
- `MerchantOrderService.cancel()` gọi `refundService.createAutoRefund(...)` cho `STORE_CANCELLED` hoặc `STORE_NO_STOCK`.
- Trước fix không có service nào cập nhật `store_reliability_stats` cho lỗi merchant.

**Fix đã làm:**
- Thêm `StoreReliabilityService`.
- `RefundService.createAutoRefund()` gọi `recordStoreFaultRefund(order, reason)` nếu reason là lỗi store.
- Các reason tính store fault: `STORE_CANCELLED`, `STORE_NO_STOCK`, `QUALITY_ISSUE`, `ALLERGEN_OR_LABELING`, `QUANTITY_SHORTAGE`.

**Ví dụ trước fix:**
- Store cancel 5 đơn paid vì hết hàng.
- Customer được auto refund, nhưng store vẫn không bị warning/suspend.

**Ví dụ sau fix:**
- Store cancel paid order với `STORE_NO_STOCK`.
- Refund auto-created.
- `merchant_cancelled_count` tăng.
- Nếu 7 ngày có >= 3 lỗi merchant hoặc fault rate vượt ngưỡng, store nhận warning/under review/suspend.

## 4. `store_reliability_stats` có bảng nhưng chưa có enforcement

**Kết luận:** Đúng.

**Vị trí vấn đề:**
- `StoreReliabilityStats` và `StoreReliabilityStatsRepository` tồn tại.
- Trước fix gần như chỉ tạo row khi store được tạo, chưa có service cập nhật/enforce.

**Fix đã làm:**
- Thêm `StoreReliabilityService`.
- Payment success gọi `recordOrderPaid()`.
- Pickup confirmed gọi `recordOrderFulfilled()`.
- No-show gọi `recordCustomerNoShow()`.
- Store fault refund gọi `recordStoreFaultRefund()`.
- Thêm các cột:
  - `merchant_cancelled_count`
  - `store_fault_refund_count`
  - `last_warning_at`
  - `last_recalculated_at`
- Rule mặc định:
  - warning nếu >= 3 lỗi merchant trong 7 ngày, hoặc fault rate >= 10% với ít nhất 10 paid orders trong 30 ngày.
  - `warning_count >= 2` thì `is_under_review=true`.
  - `warning_count >= 3` hoặc fault rate >= 25% với ít nhất 20 paid orders trong 30 ngày thì suspend 7 ngày.

**Ví dụ sau fix:**
- Store có 20 paid orders trong 30 ngày, 6 approved store-fault refunds.
- Fault rate = 30%.
- Backend set `suspended_until = now + 7 days`.
- Discovery không list bag của store này, order create trả `FORBIDDEN`.

## 5. Settlement chưa giữ tiền khi refund/dispute còn mở

**Kết luận:** Đúng.

**Vị trí vấn đề:**
- `SettlementService.createWeeklyDrafts()` trước đây dùng `LedgerEntryRepository.findUnsettledAvailable(...)`.
- Query chỉ kiểm tra `MERCHANT_PAYABLE`, `settlementId IS NULL`, `availableAt <= now`.
- Không loại order có refund status đang mở.

**Fix đã làm:**
- Thêm `LedgerEntryRepository.findUnsettledSettleableMerchantEntries(...)`.
- Settlement bỏ qua order có refund/dispute status:
  - `PENDING_REVIEW`
  - `APPROVED`
  - `PROCESSING`
  - `FAILED`
- Refund `REJECTED` thì order có thể settle.
- Refund `REFUNDED` thì settlement có thể gom ledger debit reversal để trừ merchant payable.
- `SettlementService.approve()` tự đóng settlement `netAmount <= 0` thành `PAID`, ghi audit `SETTLEMENT_CLOSE_NON_POSITIVE_NET`, không gọi PayOS payout.

**Ví dụ trước fix:**
- Order đã no-show, merchant payable available.
- Customer mở dispute `PENDING_REVIEW`.
- Weekly settlement vẫn có thể chuyển tiền cho merchant trước khi dispute xong.

**Ví dụ sau fix:**
- Cùng case trên, settlement query bỏ qua order đó.
- Nếu admin reject refund, order quay lại settle.
- Nếu refund approved/refunded, settlement sau đó tính adjustment/debit để không trả dư merchant.

## 6. Race condition khi hai bên cancel cùng lúc

**Kết luận:** Hiện tại không thấy là bug trong code chính.

**Vị trí đã kiểm tra:**
- `OrderService.cancel()` lock order bằng `orderRepository.findByIdForUpdate(...)`.
- `MerchantOrderService.cancel()` cũng lock order bằng `findByIdForUpdate(...)`.
- Release reserved stock lock theo `BagDailyStockRepository.findByBagIdAndDateForUpdate(...)`.

**Vì sao không cần fix code lớn:**
- Hai request cancel cùng order sẽ bị serialize ở DB lock.
- Sau request đầu đổi status sang terminal, request sau đọc lại status và không release stock lần hai.

**Ví dụ:**
- Customer cancel và merchant cancel cùng lúc.
- Request A lock order trước, release stock và set `CANCELLED`.
- Request B chờ lock, sau đó thấy `CANCELLED` và return current response.
- Stock không bị cộng lại hai lần.

**Lưu ý:**
- Nên bổ sung integration test concurrency riêng khi có test DB PostgreSQL để chứng minh behavior này ngoài mock/unit test.

## 7. Stock audit log thiếu actor type

**Kết luận:** Đúng.

**Vị trí vấn đề:**
- `StockAuditLog` chỉ có `actor_id` nullable.
- System jobs như expire unsold stock ghi `actor=null`, FE/admin không biết đây là system action hay dữ liệu thiếu.

**Fix đã làm:**
- Thêm enum `StockAuditActorType`:
  - `CUSTOMER`
  - `MERCHANT`
  - `ADMIN`
  - `SYSTEM`
  - `PAYMENT_PROVIDER`
- Thêm column `stock_audit_logs.actor_type` trong migration V15.
- Update response `StockAuditLogResponse.actorType`.
- Update nơi ghi audit:
  - Customer reserve/cancel: `CUSTOMER`.
  - Merchant set/adjust/cancel stock: `MERCHANT`.
  - PayOS captured sell: `PAYMENT_PROVIDER`.
  - Payment expiry/system expire unsold: `SYSTEM`.

**Ví dụ trước fix:**
```json
{
  "action": "EXPIRE_UNSOLD",
  "actorId": null
}
```
Admin không biết đây là job hệ thống hay lỗi thiếu user.

**Ví dụ sau fix:**
```json
{
  "action": "EXPIRE_UNSOLD",
  "actorId": null,
  "actorType": "SYSTEM"
}
```

## Files chính đã thay đổi

- `PickupService`: no-show notification, timeline reason/metadata, no-show reliability metric.
- `NotificationServicePort` và `NotificationService`: thêm notification `ORDER_MISSED_PICKUP`.
- `StoreReliabilityService`: service mới để cập nhật/enforce reliability.
- `RefundService`: approved store-fault refund cập nhật reliability.
- `PaymentService`: payment success cập nhật sold metric; stock audit dùng actor type `PAYMENT_PROVIDER`/`SYSTEM`.
- `SettlementService` và `LedgerEntryRepository`: hold refund/dispute mở khỏi settlement, xử lý settlement net <= 0.
- `BagDailyStockRepository`: public discovery không trả store đang suspended.
- `OrderService`: order create chặn store đang suspended.
- `StockAuditLog`, `StockAuditLogResponse`, `StockAuditActorType`: audit actor type rõ ràng.
- `V15__business_loophole_fixes.sql`: migration schema cho reliability và stock audit actor type.

## Verification

- Đã chạy `./mvnw.cmd test` thành công.
- Kết quả: 40 tests pass, 0 failures, 0 errors.