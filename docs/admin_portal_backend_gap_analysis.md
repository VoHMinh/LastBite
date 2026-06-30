# Admin Portal Backend Audit & API Gap Analysis

Generated for LastBite Admin Portal integration.
Backend path reviewed: `D:\FPT\SU_2026\EXE\EXE201\LastBite_BE`

## 1. Executive Summary

Backend hien tai da co nen tang kha day cho marketplace operations: auth/RBAC, merchant/store onboarding, store verification, bag inventory, order/payment/pickup, refund, review moderation, voucher, settlement/payout, notification va admin audit log.

Tuy nhien, de Admin Portal FE tro thanh mot dashboard BI/operations dung nghia, backend van thieu mot lop API admin aggregate rieng. Hien tai nhieu man FE phai mock data hoac tu gom tu cac API list, dan den cac van de:

- Dashboard khong co `summary` API tra KPI, alert, top stores, action queue trong mot response on dinh.
- Analytics/BI chua co API gom so lieu theo gio/ngay/thang, category, funnel, finance, user retention.
- Chua co admin global API cho orders, users, merchants, payments, store reliability.
- Mot so API admin dang la workflow queue rieng le, chua du de tao mot admin console tong hop chuyen nghiep.
- Cac chi so BI quan trong can chuan hoa dinh nghia, timezone, so sanh ky truoc va tooltip/detail de FE hien thi dung.

Ket luan ngan: BE khong yeu, nhung con thieu lop `admin dashboard + analytics read model`. Nen uu tien build cac endpoint aggregate/read-only truoc khi polish UI them, vi FE can du lieu that de het cam giac mock/chắp vá.

## 2. Scope Reviewed

Da kiem tra cac phan chinh sau:

- `README.md`, `pom.xml`, Flyway migrations, docs hien co.
- Admin controllers: store review, refund, settlement, voucher campaign/redemption, bank account, review moderation, discovery collections, bag price tiers, audit log.
- Core entities/repositories: `Order`, `Payment`, `RefundRequest`, `LedgerEntry`, `SettlementResponse`, `StoreReliabilityStats`, notification delivery, voucher analytics.
- FE Admin nhu cau hien tai: Dashboard, Analytics, Merchants, Stores, Orders & Refunds, Users, Staff Cua Hang, Notifications, Settings.

Doc lien quan da ton tai: `docs/backoffice_merchant_analytics_ui_spec.md` va `docs/frontend_business_flows.md`. File nay bo sung goc nhin audit rieng cho Admin Portal FE va API gap.

## 3. Backend Capabilities Already Available

| Domain | BE da co | API/admin readiness |
| --- | --- | --- |
| Auth/RBAC | Login, register customer/merchant/partner, refresh/logout, roles `CUSTOMER`, `MERCHANT_OWNER`, `ADMIN`, `MANAGER`, `STAFF` | Du cho auth co ban, thieu role rieng `SUPPORT`, `FINANCE`, `ANALYST` neu Admin Portal can phan quyen chi tiet |
| Store verification | List store cho duyet, xem detail, approve/reject/request changes | Co san `/api/v1/admin/stores` |
| Merchant bank account | List/detail/approve/reject bank account | Co san `/api/v1/admin/bank-accounts` |
| Bag price tier | CRUD, activate/deactivate price tiers | Co san `/api/v1/admin/bag-price-tiers` |
| Discovery/home curation | CRUD collection, activate/deactivate, manage collection items | Co san `/api/v1/admin/discovery-collections` |
| Order core | Customer order API, merchant store order API, timeline, cancel, pickup ready | Thieu admin global order API |
| Payment | PayOS webhook, payment entity/status | Thieu admin payment search/reconciliation API |
| Refund | Admin list/review/retry/manual mark transaction | Co san `/api/v1/admin/refunds`, can them analytics/summary |
| Review moderation | Report list, resolve report, hide review | Co san `/api/v1/admin/reviews` |
| Voucher/campaign | Admin campaign CRUD, approve/publish/pause/end, generate codes, analytics by campaign | Co san, can them campaign portfolio analytics |
| Settlement/payout | Draft weekly, list, approve, payout, manual mark paid/failed | Co san `/api/v1/admin/settlements`, can them finance BI/read model |
| Ledger | Double-entry style ledger for cash, escrow, revenue, merchant payable, refund | Data tot cho finance dashboard, thieu read-only admin ledger endpoints |
| Notifications | User inbox, admin broadcast, stats | Co san, nhung stats con mong cho BI notification |
| Audit log | Search admin audit logs | Co san `/api/v1/admin/audit-logs` |
| Store reliability | Entity/service tinh warning, under review, suspendedUntil, fault refund rate | Logic co san, thieu admin endpoint expose danh sach/risk |
| Media/docs | Presigned upload, confirm, access-url | Dung duoc cho review document/store proof |

## 4. Admin APIs Already Exposed

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/admin/stores?verificationStatus=&page=&size=` | Store review queue |
| GET | `/api/v1/admin/stores/{storeId}` | Store review detail |
| PATCH | `/api/v1/admin/stores/{storeId}/approve` | Approve store |
| PATCH | `/api/v1/admin/stores/{storeId}/reject` | Reject store |
| PATCH | `/api/v1/admin/stores/{storeId}/request-changes` | Request changes |
| GET | `/api/v1/admin/bank-accounts?status=&businessProfileId=&storeId=&page=&size=` | Bank verification queue |
| GET | `/api/v1/admin/bank-accounts/{bankAccountId}` | Bank detail |
| PATCH | `/api/v1/admin/bank-accounts/{bankAccountId}/approve` | Approve bank account |
| PATCH | `/api/v1/admin/bank-accounts/{bankAccountId}/reject` | Reject bank account |
| GET | `/api/v1/admin/refunds?status=&reason=&page=&size=` | Refund queue |
| POST | `/api/v1/admin/refunds/{refundId}/review` | Approve/reject refund |
| POST | `/api/v1/admin/refunds/{refundId}/retry` | Retry refund transaction |
| POST | `/api/v1/admin/refunds/transactions/{transactionId}/mark-succeeded` | Manual refund success |
| POST | `/api/v1/admin/refunds/transactions/{transactionId}/mark-failed` | Manual refund failed |
| POST | `/api/v1/admin/settlements/draft-weekly` | Create weekly settlement drafts |
| GET | `/api/v1/admin/settlements?status=&page=&size=` | Settlement list |
| POST | `/api/v1/admin/settlements/{settlementId}/approve` | Approve settlement |
| POST | `/api/v1/admin/settlements/{settlementId}/payout` | Trigger payout |
| POST | `/api/v1/admin/settlements/payouts/{payoutId}/mark-paid` | Manual payout paid |
| POST | `/api/v1/admin/settlements/payouts/{payoutId}/mark-failed` | Manual payout failed |
| GET | `/api/v1/admin/voucher-campaigns?...` | Campaign list |
| POST | `/api/v1/admin/voucher-campaigns` | Create campaign |
| PATCH | `/api/v1/admin/voucher-campaigns/{campaignId}` | Update campaign |
| POST | `/api/v1/admin/voucher-campaigns/{campaignId}/approve` | Approve campaign |
| POST | `/api/v1/admin/voucher-campaigns/{campaignId}/publish` | Publish campaign |
| POST | `/api/v1/admin/voucher-campaigns/{campaignId}/pause` | Pause campaign |
| POST | `/api/v1/admin/voucher-campaigns/{campaignId}/end` | End campaign |
| POST | `/api/v1/admin/voucher-campaigns/{campaignId}/codes` | Generate codes |
| GET | `/api/v1/admin/voucher-campaigns/{campaignId}/analytics` | Campaign analytics |
| GET | `/api/v1/admin/voucher-redemptions?...` | Voucher redemption list |
| GET | `/api/v1/admin/reviews/reports?status=&page=&size=` | Review report queue |
| POST | `/api/v1/admin/reviews/reports/{reportId}/resolve` | Resolve report |
| POST | `/api/v1/admin/reviews/{reviewId}/hide` | Hide review |
| GET | `/api/v1/admin/discovery-collections` | Discovery collection list |
| POST/PATCH | `/api/v1/admin/discovery-collections...` | Manage homepage collections/items |
| GET | `/api/v1/admin/bag-price-tiers` | Price tier list |
| POST/PATCH | `/api/v1/admin/bag-price-tiers...` | Manage price tiers |
| GET | `/api/v1/admin/audit-logs?...` | Search audit logs |
| POST | `/api/v1/notifications/admin/broadcast` | Broadcast notification |
| GET | `/api/v1/notifications/admin/stats` | FCM/device stats |

Note: notification admin routes hien nam duoi `/api/v1/notifications/admin/*`, khong nam duoi `/api/v1/admin/notifications/*`. FE nen map ro de tranh nham namespace.

## 5. Main Gaps Blocking A Full Admin Portal

### P0 - Can Lam Truoc De FE Admin Het Mock

#### 5.1 Admin Dashboard Summary API

FE Dashboard can mot API tong hop nhanh, tra dung cac block dau trang:

```http
GET /api/v1/admin/dashboard/summary?date=2026-06-26&timezone=Asia/Ho_Chi_Minh
```

Nen tra:

- Business health: GMV, completed orders, completion rate, active selling stores.
- Operations queue: pending merchant/store review, pending bank accounts, pending refunds, open review reports, stores under review.
- Alerts: refund rate spike, conversion drop, payment failure spike, stores suspended/offline, settlement failed.
- Top stores today: GMV, orders, category, refund/issue flag.
- Mini time series: today hourly GMV vs yesterday same hours.

Vi sao can: Dashboard admin khong nen goi 6-10 API roi FE tu tinh. KPI can dong nhat ve timezone, range, compare period va cach tinh.

#### 5.2 Admin Analytics API

```http
GET /api/v1/admin/analytics/sales?range=today|7d|30d|quarter&granularity=hour|day|month&compare=true&timezone=Asia/Ho_Chi_Minh
GET /api/v1/admin/analytics/funnel?from=&to=&timezone=
GET /api/v1/admin/analytics/categories?from=&to=&metric=orders|gmv|aov
GET /api/v1/admin/analytics/top-stores?from=&to=&sort=gmv|orders|refundRate
GET /api/v1/admin/analytics/refunds?from=&to=&groupBy=reason|status|store|day
GET /api/v1/admin/analytics/finance/reconciliation?from=&to=
GET /api/v1/admin/analytics/users?from=&to=
```

Nen tra data da san sang cho chart:

- `points`: label/time/current/previous/delta.
- `summary`: total, previousTotal, deltaPercent.
- `legend`: label, unit, colorKey neu can.
- `tooltip`: cac field FE hien thi khi hover.
- `emptyStateReason`: neu khong co data.

#### 5.3 Admin Orders API

Hien BE co customer order API va merchant-scoped order API, nhung Admin Portal can search toan he thong:

```http
GET /api/v1/admin/orders?status=&paymentStatus=&refundStatus=&storeId=&customerId=&from=&to=&keyword=&page=&size=&sort=
GET /api/v1/admin/orders/{orderId}
GET /api/v1/admin/orders/{orderId}/timeline
POST /api/v1/admin/orders/{orderId}/cancel
POST /api/v1/admin/orders/{orderId}/add-note
```

FE `Orders & Refunds` can order detail gom payment, refund, timeline, store, customer, ledger hints. Khong nen bat admin phai vao tung merchant/store scope.

#### 5.4 Admin Users API

Hien chi co `/api/v1/users/me`. Admin Portal can:

```http
GET /api/v1/admin/users?role=&status=&keyword=&createdFrom=&createdTo=&page=&size=
GET /api/v1/admin/users/{userId}
PATCH /api/v1/admin/users/{userId}/status
GET /api/v1/admin/users/{userId}/orders
GET /api/v1/admin/users/{userId}/activity
```

Can cho support/admin tra cuu customer, merchant owner, manager/staff, xem history, khoa/mo tai khoan, reset flags neu can.

#### 5.5 Admin Merchants API

Hien co business profile cho merchant owner va bank/store review, nhung chua co admin merchant master view:

```http
GET /api/v1/admin/merchants?status=&verificationStatus=&keyword=&page=&size=
GET /api/v1/admin/merchants/{merchantId}
GET /api/v1/admin/merchants/{merchantId}/stores
GET /api/v1/admin/merchants/{merchantId}/documents
GET /api/v1/admin/merchants/{merchantId}/risk-summary
```

Admin Portal can nhin merchant nhu mot account/business: legal profile, owner, docs, bank accounts, stores, settlements, refund risk, audit trail.

#### 5.6 Admin Store Search + Reliability API

Hien `/api/v1/admin/stores` nghieng ve verification queue. Can them list/search cho toan bo store:

```http
GET /api/v1/admin/stores/search?keyword=&category=&status=&verificationStatus=&city=&underReview=&page=&size=
GET /api/v1/admin/stores/{storeId}/reliability
GET /api/v1/admin/stores/reliability?underReview=true&suspended=true&page=&size=
PATCH /api/v1/admin/stores/{storeId}/suspend
PATCH /api/v1/admin/stores/{storeId}/unsuspend
```

BE da co `StoreReliabilityStats` va `StoreReliabilityService`, gom: sold/fulfilled/no-show, merchant cancelled, store fault refund, fulfillment rate, warning count, under review, suspended until. Nen expose cho Admin Portal de lam risk queue.

### P1 - Nang Cap BI/Operations Chuyen Nghiep

#### 5.7 Finance / Ledger Read APIs

Settlement workflow da co, nhung finance dashboard can read model:

```http
GET /api/v1/admin/finance/overview?from=&to=
GET /api/v1/admin/finance/ledger-entries?accountType=&entryType=&from=&to=&page=&size=
GET /api/v1/admin/finance/reconciliation?from=&to=
GET /api/v1/admin/payments?status=&provider=&from=&to=&page=&size=
GET /api/v1/admin/payments/{paymentId}
```

Nen gom GMV, gross captured, refunds, platform fee, merchant payable, settlement paid, pending payout, failed payout, discrepancy.

#### 5.8 BI Event Tracking For True Funnel

Neu FE Analytics hien funnel cho LastBite/Too Good To Go style thi khong nen co buoc gio hang. Funnel dung nen la `Xem store/bag -> Dat tui -> Thanh toan -> Pickup/Hoan tat`. Backend co `store_engagement_events` va `/api/v1/analytics/engagement-events` de track view/click, con order/payment tinh duoc tu checkout tro di.

Con can them cho admin/global funnel va event batch neu muon tracking rong hon:

```http
POST /api/v1/analytics/events/batch
GET /api/v1/admin/analytics/funnel?from=&to=
```

Event types de can nhac:

- Da co: `STORE_VIEW`, `STORE_CARD_CLICK`, `BAG_VIEW`, `BAG_CARD_CLICK`
- Co the them: `APP_OPEN`, `HOME_VIEW`, `BAG_IMPRESSION`, `SEARCH_SUBMITTED`
- `CHECKOUT_STARTED`, `PAYMENT_LINK_CREATED`, `PAYMENT_PAID`
- `ORDER_COMPLETED`, `REFUND_REQUESTED`

Neu thieu event cho tung buoc thi funnel buoc do chi la approximation, de BI dashboard se bi sai ban chat.

#### 5.9 Export / Report Jobs

FE co tab `Xuat bao cao`, nen BE can async export:

```http
POST /api/v1/admin/reports/exports
GET /api/v1/admin/reports/exports/{exportId}
GET /api/v1/admin/reports/exports/{exportId}/download-url
```

Request nen co: reportType, from, to, filters, format `CSV|XLSX`, timezone. Nen chay async de tranh timeout.

#### 5.10 Notification Analytics

Hien co broadcast va stats, nhung BI can:

```http
GET /api/v1/admin/notifications/campaigns?from=&to=&page=&size=
GET /api/v1/admin/notifications/analytics?from=&to=&groupBy=type|channel|day
GET /api/v1/admin/notifications/{notificationId}/deliveries?status=&page=&size=
```

Entity `NotificationDelivery` co channel/status/sentAt/retryCount, du de lam delivery stats. Neu muon open/click/conversion thi can them tracking event.

#### 5.11 Admin Staff / Permission Management

BE co role co ban va merchant store members, nhung Admin Portal can quan ly nhan su noi bo LastBite:

```http
GET /api/v1/admin/staff?role=&status=&page=&size=
POST /api/v1/admin/staff
PATCH /api/v1/admin/staff/{userId}
PATCH /api/v1/admin/staff/{userId}/deactivate
GET /api/v1/admin/roles
```

Nen can nhac role tach biet:

- `ADMIN`: full access.
- `SUPPORT`: order/refund/customer support, khong payout.
- `FINANCE`: ledger/settlement/payout, khong store moderation.
- `OPS`: merchant/store/reliability moderation.
- `ANALYST`: read-only analytics/export.

### P2 - Sau Khi Dashboard Chay Tot

- Alert rule management: nguong refund rate, payment failure, store fault, low completion.
- Admin notes/internal comments tren merchant/store/order/refund.
- Saved views/filter presets cho admin table.
- Scheduled reports gui email.
- Anomaly detection jobs tao alert tu dong.
- Data warehouse/fact table neu traffic lon: `fact_orders_daily`, `fact_store_daily`, `fact_user_daily`.

## 6. Metrics Definitions FE/BE Should Standardize

| Metric | Dinh nghia de xuat | Source |
| --- | --- | --- |
| GMV | Tong `orders.finalAmount` cua order da thanh toan trong range, tru/khong tru refund can chon ro. De dashboard nen dung gross paid GMV va hien refund rieng | `orders.paidAt`, `orders.finalAmount` |
| Net revenue | Platform fee/commission thuc nhan sau refund/reversal | `ledger_entries`, `platform_commissions` |
| Completed orders | Order status `PICKED_UP` hoac flow hoan tat/no-show tuy business rule. Can chuan hoa ten `completed` | `orders.status`, `pickedUpAt` |
| Completion rate | completed orders / paid orders trong range | `orders` |
| Sell-through | bags sold / bags listed trong range | `bag_daily_stock`, `orders.quantity` |
| Take rate | platform fee / GMV | `orders.platformFee`, `platform_commissions` |
| AOV | GMV / completed or paid orders | `orders.finalAmount` |
| Refund rate | approved/refunded amount or refund count / GMV or paid orders. UI phai ghi ro theo amount hay count | `refund_requests`, `orders` |
| Repeat purchase | customers co >= 2 paid orders / active customers trong range | `orders.user_id` |
| New vs returning | first paid order in range vs da co paid order truoc range | `orders.user_id`, `paidAt` |
| Active stores | stores co bag active hoac co paid order trong range. Can chon ro | `stores`, `surprise_bags`, `orders` |
| Category share | orders/GMV theo category cua bag/store tai thoi diem order | `orders.bag.category`; nen snapshot category neu can chinh xac lich su |

Tat ca analytics API nen nhan `timezone`, mac dinh `Asia/Ho_Chi_Minh`. Range `today` phai tinh theo gio Viet Nam, khong dung UTC/server local mot cach ngam dinh.

## 7. Suggested API Response Shape For Charts

FE dang gap van de chart thieu scale/tooltip/legend. Backend nen tra payload chart-friendly:

```json
{
  "range": {
    "from": "2026-06-01T00:00:00+07:00",
    "to": "2026-06-30T23:59:59+07:00",
    "granularity": "DAY",
    "timezone": "Asia/Ho_Chi_Minh"
  },
  "unit": "VND",
  "summary": {
    "currentTotal": 428600000,
    "previousTotal": 381200000,
    "deltaPercent": 12.4
  },
  "series": [
    {
      "key": "current",
      "label": "Ky hien tai",
      "points": [
        { "time": "2026-06-01", "label": "01/06", "value": 9800000 }
      ]
    },
    {
      "key": "previous",
      "label": "Ky truoc",
      "points": [
        { "time": "2026-05-01", "label": "01/05", "value": 8100000 }
      ]
    }
  ]
}
```

Loi ich: FE co the hien truc Y, legend, tooltip va compare dung ma khong can doan y nghia field.

## 8. What Admin Portal Should Add From Existing BE Now

Nhung man nay BE da co API kha ro, FE nen tan dung truoc:

1. Store verification queue: list/detail/approve/reject/request changes.
2. Bank account verification queue: approve/reject account payout.
3. Refund operations: list/review/retry/manual transaction result.
4. Settlement/payout console: draft weekly, approve, payout, mark paid/failed.
5. Voucher campaign admin: campaign lifecycle, codes, redemption list, campaign analytics.
6. Discovery collection manager: quan ly collection hien tren home/discovery.
7. Bag price tier manager: category/bag-size price rules.
8. Review moderation queue: reported reviews, resolve/hide.
9. Notification broadcast: gui broadcast va xem FCM stats co ban.
10. Audit log viewer: filter actor/action/target/from/to.

Day la cac phan giup Admin Portal trong co gia tri that ngay ca khi BI aggregate chua xong.

## 9. Recommended Implementation Phases

### Phase 1 - Operational Admin MVP

Muc tieu: Admin Portal khong con mock o cac man van hanh.

- Add `AdminDashboardController` voi `summary`, `alerts`, `action-queue`.
- Add `AdminOrderController` global order search/detail/timeline.
- Add `AdminUserController` user search/detail/status.
- Add `AdminMerchantController` merchant master list/detail.
- Add `AdminStoreSearchController` hoac mo rong `AdminStoreController` cho search toan bo store.
- Expose `StoreReliabilityStats` cho risk queue.

### Phase 2 - BI Analytics Layer

Muc tieu: Chart co du lieu that, tooltip ro, khong FE-side guess.

- Add `AdminAnalyticsController`.
- Add repository projections/native queries cho sales time series, top stores, category share, refund analytics.
- Add finance overview tu ledger/commission/settlement.
- Chuan hoa metric definitions trong DTO.
- Add caching cho dashboard/analytics ngan han neu query nang.

### Phase 3 - Enterprise Admin Quality

Muc tieu: gan voi chuan enterprise admin cua Google/Microsoft: ro role, ro audit, ro export.

- Add internal admin staff + permission model.
- Add report export jobs.
- Add notification analytics chi tiet.
- Add alert rules/anomaly jobs.
- Add event tracking cho funnel that.
- Add saved filters/views.

## 10. Suggested Package Structure

De giu code sach, khong nen nhét analytics vao tung module rieng le qua nhieu. Co the tao module rieng:

```text
src/main/java/com/LastBite/modules/admin/
  dashboard/
    controller/AdminDashboardController.java
    service/AdminDashboardService.java
    dto/AdminDashboardSummaryResponse.java
  analytics/
    controller/AdminAnalyticsController.java
    service/AdminAnalyticsService.java
    repository/AdminAnalyticsRepository.java
    dto/TimeSeriesResponse.java
  operations/
    controller/AdminOrderController.java
    controller/AdminUserController.java
    controller/AdminMerchantController.java
```

Neu muon giu module theo domain, van co the dat controller trong tung module (`order/controller/AdminOrderController.java`), nhung DTO chart/summary nen dung chung de FE khong bi moi endpoint mot format.

## 11. Data Risks To Fix Before BI Looks "Real"

- Category/time-series lich su: neu category/store name/bag info thay doi sau order, chart lich su co the doi theo. Nen snapshot cac field can BI vao order hoac fact table.
- Funnel: khong co event tracking thi khong nen hien `view -> checkout` nhu so lieu that.
- Refund rate: phai ro tinh theo count hay amount, va status nao duoc tinh.
- Completion rate: phai ro no-show co tinh completed hay khong.
- Timezone: dashboard today/this month phai dung `Asia/Ho_Chi_Minh`.
- Performance: dashboard khong nen query raw order/payment/refund qua nhieu lan moi request. Nen dung projection/native aggregate va cache ngan han.
- Permissions: `ADMIN` all-powerful du cho MVP, nhung finance/refund/payout nen tach role truoc production.

## 12. Final Recommendation

Thu tu nen lam de FE Admin Portal len chat nhanh nhat:

1. Build `GET /api/v1/admin/dashboard/summary` va `GET /api/v1/admin/dashboard/action-queue`.
2. Build `GET /api/v1/admin/analytics/sales`, `categories`, `top-stores`, `refunds`.
3. Build admin global `orders`, `users`, `merchants`, `stores/search`.
4. Expose store reliability/risk queue.
5. Add finance overview + ledger/payment read APIs.
6. Add export jobs va BI event tracking sau khi core dashboard da dung.

Backend hien tai du nen de lam Admin Portal tot, nhung can them lop aggregate/read model. Neu khong co lop nay, FE se tiep tuc phai dung mock hoặc gom data tu nhieu endpoint, UI se kho dat cam giac BI chuyen nghiep va kho bao tri.
