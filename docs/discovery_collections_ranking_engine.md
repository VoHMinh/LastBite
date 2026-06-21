# Curated Collections + Ranking Engine

## 1. Muc tieu

Tinh nang nay tao lop Discovery moi cho man Home cua LastBite:

- Gom bag dang ban trong ngay thanh cac collection co y nghia voi customer.
- Sap xep bag bang ranking engine co cong thuc ro rang, co config trong DB.
- Cho admin quan ly collection, title, thu tu hien thi, trang thai active, rule params va manual items.
- Giu backward compatibility cho `/api/v1/bags/today` va `/api/v1/bags/nearby`; default sort van la `pickup_time`.
- Mo rong search/discovery voi `sort=relevance` dung chung Ranking Engine.

Scope batch nay la MVP co 8 collections seeded san:

1. `near_you`
2. `last_chance`
3. `big_discount`
4. `under_30k`
5. `new_stores`
6. `top_rated`
7. `bestseller_today`
8. `recommended_for_you`

## 2. Nen tang hien co da tan dung

Du an truoc feature nay da co nhung thanh phan cot loi cho Discovery:

- `bag_daily_stocks`: stock theo ngay, gom `stockDate`, `quantity`, `reserved`, `sold`, pickup window.
- `surprise_bags`: thong tin bag, gia tri toi thieu, gia ban co so, dynamic pricing, category, diet, type.
- `stores`: dia diem, rating, total rating, category, created date.
- `orders`: order status, `paidAt`, relation den bag/store/user.
- `favorite_stores`: favorite store theo user.
- `user_discovery_preferences`: default location/radius, diet preference, preferred collection time.
- Redis cache cho bag discovery/detail/store list.
- Scheduled/business flows da co quanh payment, pickup, stock va pricing.

Feature moi khong thay the cac endpoint cu. No them mot lop Discovery/Ranking dung chung tren projection public bag hien co.

## 3. Mapping field thuc te

Ranking engine dung `PublicBagSummaryResponse`, duoc map tu `BagDiscoveryProjection`.

| Concept | Field thuc te | Ghi chu |
| --- | --- | --- |
| Gia tri goc | `minimumValue` | Gia tri toi thieu merchant khai bao cho bag. |
| Gia ban hien tai | `currentSalePrice` | Gia sau dynamic pricing/promotion neu co. |
| Phan tram giam | `currentDiscountPercent` | Tinh tu `minimumValue` va `currentSalePrice`. |
| Stock kha dung | `available` | `quantity - reserved - sold`, clamp khong am. |
| Sold-out | `soldOut` | `available <= 0`. |
| Rating store | `storeAvgRating` | Map tu `stores.avg_rating`. |
| So rating | `storeTotalRatings` | Map tu `stores.total_ratings`. |
| Khoang cach | `distanceKm` | Chi co khi request co lat/lng hoac user co default location. |
| Pickup end | `stockDate` + `pickupEndTime` | Dung cho urgency. |
| Store moi | `storeCreatedAt` | Lay tu `stores.created_at`. |
| Bestseller | `ordersTodayCount` | Count order paid trong ngay pickup, loai `CANCELLED`, `REFUNDED`. |

## 4. Database schema moi

Migration: `src/main/resources/db/migration/V17__discovery_collections_ranking_engine.sql`.

### `platform_configs`

Dung cho config platform dang JSONB.

- `config_key`: primary key, vi du `discovery.ranking`.
- `config_value`: JSONB config.
- `description`, `created_at`, `updated_at`.

Seed `discovery.ranking` gom weights, nguong rating, urgency window va personalization boosts.

### `discovery_collections`

Quan ly metadata/rule cua Home Discovery.

- `id UUID`
- `slug`
- `title`
- `type`: `RULE_BASED`, `CURATED_MANUAL`, `PERSONALIZED`
- `rule_definition JSONB`
- `display_order`
- `max_items`
- `min_items_to_display`
- `is_active`
- timestamps

`rule_definition` la typed JSON, khong chua raw SQL:

```json
{
  "rule": "BIG_DISCOUNT",
  "params": {
    "minDiscountPercent": 35
  },
  "sort": "DISCOUNT_DESC"
}
```

Backend validate `rule` va `sort` bang enum allowlist, tranh dynamic SQL nguy hiem.

### `discovery_collection_items`

Dung cho collection manual curated.

- `collection_id`
- `bag_id`
- `pinned_order`
- `added_at`

Unique theo `(collection_id, bag_id)`.

### `bag_ranking_cache`

Bang persistent cache/precompute cho ranking components theo stock trong ngay.

- Primary key: `daily_stock_id`
- `bag_id`
- `rating_score`
- `discount_score`
- `urgency_score`
- `availability_score`
- `computed_at`

Ly do key bang `daily_stock_id`: LastBite discovery phu thuoc stock theo ngay, pickup window va available inventory, khong chi phu thuoc static `bag_id`.

Online ranking trong batch nay van compute tu live projection de giu chinh xac cho distance/location va dynamic pricing. Bang cache san sang cho batch sau them scheduled precompute neu traffic Home tang cao.

## 5. Ranking formula

Service: `DiscoveryRankingService`.

Default config:

```json
{
  "weights": {
    "distance": 0.30,
    "urgency": 0.20,
    "discount": 0.20,
    "rating": 0.15,
    "availability": 0.15
  },
  "maxRadiusKm": 5.0,
  "minReachableMinutes": 15,
  "urgencyWindowMinutes": 120,
  "minReviewsThreshold": 5,
  "defaultNeutralRatingScore": 0.8,
  "availabilityNormalizeCap": 10,
  "favoriteStoreBoost": 0.20,
  "categoryHistoryBoost": 0.15
}
```

Scores duoc normalize ve `[0, 1]`:

```text
distanceScore = max(0, 1 - distanceKm / radiusKm)
```

Neu khong co location thi distance score bang `0`.

```text
urgencyScore = 0 neu minutesLeft < minReachableMinutes
urgencyScore = clamp(1 - (minutesLeft - minReachableMinutes) / urgencyWindowMinutes)
```

`minutesLeft` tinh tu thoi diem hien tai den `stockDate + pickupEndTime`.

```text
discountScore = max(0, minimumValue - currentSalePrice) / minimumValue
```

```text
ratingScore = avgRating / 5
```

Cold start: neu `totalRatings < minReviewsThreshold`, dung `defaultNeutralRatingScore = 0.8`.

```text
availabilityScore = min(1, available / availabilityNormalizeCap)
```

Final score:

```text
finalScore =
  0.30 * distanceScore +
  0.20 * urgencyScore +
  0.20 * discountScore +
  0.15 * ratingScore +
  0.15 * availabilityScore
```

Tie-break mac dinh:

- Sold-out luon xuong sau.
- Neu cung score, uu tien pickup start time som hon.

## 6. Collection rule definitions

Seeded collections:

| Slug | Type | Rule | Params | Sort | Y nghia |
| --- | --- | --- | --- | --- | --- |
| `near_you` | `RULE_BASED` | `NEAR_YOU` | `maxDistanceKm: 5` | `DISTANCE_ASC` | Bag gan user nhat. |
| `last_chance` | `RULE_BASED` | `LAST_CHANCE` | `{}` | `RANKING_DESC` | Bag con trong urgency window va van du thoi gian toi pickup. |
| `big_discount` | `RULE_BASED` | `BIG_DISCOUNT` | `minDiscountPercent: 35` | `DISCOUNT_DESC` | Bag dang giam manh. |
| `under_30k` | `RULE_BASED` | `UNDER_PRICE` | `maxPrice: 30000` | `RANKING_DESC` | Bag co gia hien tai <= 30,000 VND. |
| `new_stores` | `RULE_BASED` | `NEW_STORES` | `days: 30` | `STORE_CREATED_DESC` | Store moi, sort theo ngay tao store, bypass ranking. |
| `top_rated` | `RULE_BASED` | `TOP_RATED` | `minRating: 4.0`, `minReviews: 5` | `RATING_DESC` | Store rating cao va co du review. |
| `bestseller_today` | `RULE_BASED` | `BESTSELLER_TODAY` | `{}` | `ORDERS_TODAY_DESC` | Dua tren paid orders hom nay, loai cancelled/refunded. |
| `recommended_for_you` | `PERSONALIZED` | `RECOMMENDED_FOR_YOU` | `{}` | `PERSONALIZED_DESC` | Ranking + favorite store boost + category history boost. |

Rule-based collections duoc hide neu so item sau filter/sort nho hon `min_items_to_display`.

Khong dedupe bag giua cac collection. Mot bag tot co the xuat hien o `near_you`, `big_discount` va `recommended_for_you` cung luc.

## 7. Public API contract

### Home Discovery

```http
GET /api/v1/home/discovery?lat=10.7769&lng=106.7009&radius=5
```

Public GET, optional JWT. Neu co JWT, service dung user id de lay default preference, favorite store va purchase category history.

Response body trong `ApiResponse.data`:

```json
[
  {
    "id": "collection-uuid",
    "slug": "big_discount",
    "title": "Dang giam manh",
    "type": "RULE_BASED",
    "displayOrder": 30,
    "items": [
      {
        "bagId": "bag-uuid",
        "storeId": "store-uuid",
        "name": "Meal bag",
        "minimumValue": 100000,
        "currentSalePrice": 65000,
        "currentDiscountPercent": 35,
        "available": 3,
        "storeAvgRating": 4.7,
        "storeTotalRatings": 16,
        "distanceKm": 1.2,
        "favoriteStore": false
      }
    ]
  }
]
```

Rules:

- Collection sort theo `displayOrder`.
- Collection inactive khong tra ve.
- Collection co item count `< min_items_to_display` khong tra ve.
- `recommended_for_you` bi hide voi anonymous user de tranh goi y gay hieu nham.

### Bag discovery/search

Existing endpoints giu default sort cu:

```http
GET /api/v1/bags/today?sort=pickup_time
GET /api/v1/bags/nearby?lat=&lng=&radius=&sort=pickup_time
```

Them `sort=relevance`:

```http
GET /api/v1/bags/today?lat=10.7769&lng=106.7009&radius=5&sort=relevance
GET /api/v1/bags/nearby?lat=10.7769&lng=106.7009&radius=5&sort=relevance
```

Search moi:

```http
GET /api/v1/bags/search?q=banh&lat=10.7769&lng=106.7009&radius=5&sort=relevance&limit=20
```

Supported sort:

- `relevance`
- `distance`
- `price`
- `rating`
- `pickup_time`

Supported filters:

- `category`
- `dietType`
- `bagType`
- `district`
- `limit`

## 8. Admin API contract

Base path:

```http
/api/v1/admin/discovery-collections
```

Endpoints:

```http
GET    /api/v1/admin/discovery-collections
GET    /api/v1/admin/discovery-collections/{id}
POST   /api/v1/admin/discovery-collections
PATCH  /api/v1/admin/discovery-collections/{id}
PATCH  /api/v1/admin/discovery-collections/{id}/activate
PATCH  /api/v1/admin/discovery-collections/{id}/deactivate
GET    /api/v1/admin/discovery-collections/{id}/items
POST   /api/v1/admin/discovery-collections/{id}/items
PATCH  /api/v1/admin/discovery-collections/{id}/items/{itemId}
DELETE /api/v1/admin/discovery-collections/{id}/items/{itemId}
```

Create example:

```json
{
  "slug": "weekend_picks",
  "title": "Cuoi tuan nen thu",
  "type": "RULE_BASED",
  "ruleDefinition": {
    "rule": "TOP_RATED",
    "params": {
      "minRating": 4.3,
      "minReviews": 10
    },
    "sort": "RATING_DESC"
  },
  "displayOrder": 90,
  "maxItems": 10,
  "minItemsToDisplay": 3,
  "active": true
}
```

Admin co the doi title/order/threshold/max items ma khong deploy lai backend, nhung chi trong enum/rule allowlist.

Ranking weights chua co admin API trong batch nay. Config da seed va read tu DB de batch sau them admin UI/API don gian hon.

## 9. Personalization MVP

`recommended_for_you` khong dung ML/collaborative filtering trong batch nay.

Score ca nhan hoa:

```text
personalizedScore = baseRankingScore
                  + favoriteStoreBoost neu store nam trong favorite
                  + categoryHistoryBoost neu category tung duoc user mua
```

Nguon du lieu:

- Favorite store: `favorite_stores`.
- Category history: paid orders cua user, loai `CANCELLED` va `REFUNDED`.
- Preference location: `user_discovery_preferences.defaultLat/defaultLng/defaultRadiusKm` neu request khong truyen lat/lng.

Anonymous user:

- `recommended_for_you` khong hien thi.
- Cac collection rule-based van hien thi theo request location hoac fallback khong location.

## 10. Cache, performance va edge cases

### Cache

- `discovery-config`: TTL 5 phut, cache config `discovery.ranking`.
- `bag-discovery`: TTL 60 giay, dung cho today/nearby/search.
- `home-discovery`: TTL 60 giay, key theo `userId`, `lat`, `lng`, `radius`.
- Admin thay doi collection evict `home-discovery` va `bag-discovery`.
- User doi discovery preference/favorite store evict `home-discovery` va `bag-discovery`.
- Cac flow lam thay doi bag/order/store/promotion/stock da evict them `home-discovery` de Home khong stale.

### Query strategy

- Home Discovery load candidate pool mot lan, toi da 300 rows, sau do apply rule/sort trong service.
- Search/relevance sort lay candidate pool rong hon khi can ranking tren Java side.
- Native query tinh `ordersTodayCount` bang paid orders trong ngay stock va loai `CANCELLED`, `REFUNDED`.
- Location filtering chi apply khi co lat/lng; neu khong co location, `distanceKm` null va distance score bang 0.

### Edge cases

- Lat/lng phai di cung nhau; thieu mot trong hai se tra `INVALID_INPUT`.
- Radius phai `> 0` va `<= 50` km.
- `minimumValue <= 0` hoac price null: discount score bang 0.
- `available <= 0`: availability score bang 0 va sold-out sort xuong sau.
- Pickup end qua gan hon `minReachableMinutes`: urgency score bang 0.
- Rating cold start: store it review duoc neutral score 0.8, khong bi phat qua nang.
- Collection khong du item theo `min_items_to_display` se bi an.

## 11. Test coverage

Da them/cap nhat tests:

- `DiscoveryRankingServiceTest`
  - normalize score dung.
  - cold-start rating dung neutral score.
  - urgency duoi `minReachableMinutes` bang 0.
  - availability cang nhieu cang cao.
- `HomeDiscoveryServiceTest`
  - migration seed du 8 collection.
  - hide collection khi item count duoi minimum.
  - `new_stores` sort theo store created date va bypass ranking.
  - anonymous user khong thay `recommended_for_you`.
  - `bestseller_today` sort theo paid orders count.
- `BagDiscoveryServiceTest`
  - giu default sort cu.
  - them `sort=relevance`.
  - them keyword search.

Verification command:

```powershell
.\mvnw.cmd test
```

Ky vong hien tai:

```text
Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 12. Implementation map

Core files:

- `src/main/resources/db/migration/V17__discovery_collections_ranking_engine.sql`
- `src/main/java/com/LastBite/modules/discovery/service/DiscoveryRankingService.java`
- `src/main/java/com/LastBite/modules/discovery/service/HomeDiscoveryService.java`
- `src/main/java/com/LastBite/modules/discovery/service/AdminDiscoveryCollectionService.java`
- `src/main/java/com/LastBite/modules/discovery/controller/HomeDiscoveryController.java`
- `src/main/java/com/LastBite/modules/discovery/controller/AdminDiscoveryCollectionController.java`
- `src/main/java/com/LastBite/modules/bag/service/impl/BagDiscoveryService.java`
- `src/main/java/com/LastBite/modules/bag/service/impl/BagDiscoveryMapper.java`
- `src/main/java/com/LastBite/modules/bag/repository/BagDailyStockRepository.java`
- `src/main/java/com/LastBite/modules/order/repository/OrderRepository.java`
- `src/main/java/com/LastBite/common/security/SecurityConfig.java`
- `src/main/java/com/LastBite/common/config/CacheConfig.java`

DTO/entity/repository folders:

- `src/main/java/com/LastBite/modules/discovery/dto`
- `src/main/java/com/LastBite/modules/discovery/entity`
- `src/main/java/com/LastBite/modules/discovery/enums`
- `src/main/java/com/LastBite/modules/discovery/repository`

Tests:

- `src/test/java/com/LastBite/modules/discovery/service/DiscoveryRankingServiceTest.java`
- `src/test/java/com/LastBite/modules/discovery/service/HomeDiscoveryServiceTest.java`
- `src/test/java/com/LastBite/modules/bag/service/BagDiscoveryServiceTest.java`

## 13. Van hanh

Khi deploy:

1. Chay migration V17.
2. Kiem tra seed collection:
   - `SELECT slug, type, display_order, is_active FROM discovery_collections ORDER BY display_order;`
3. Kiem tra config:
   - `SELECT config_key, config_value FROM platform_configs WHERE config_key = 'discovery.ranking';`
4. Test smoke public API:
   - `GET /api/v1/home/discovery?lat=10.7769&lng=106.7009&radius=5`
   - `GET /api/v1/bags/search?q=banh&sort=relevance&limit=10`
5. Test admin API bang role admin:
   - update title/order cua mot collection.
   - deactivate/reactivate collection.
   - them manual item vao collection `CURATED_MANUAL`.

Can theo doi sau deploy:

- Home latency khi candidate pool tang.
- Ty le collection bi hide vi khong du `min_items_to_display`.
- So luong empty `near_you` neu user khong cap location.
- Conversion cua `recommended_for_you` so voi rule-based collections.
