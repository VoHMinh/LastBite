# Merchant stock forecast flow

## Bag template

When creating or updating a bag, FE can send `weeklyStockPlan` together with
`availableDays`.

```json
{
  "availableDays": [1, 2, 3, 4, 5],
  "weeklyStockPlan": [
    { "dayOfWeek": 1, "quantity": 10 },
    { "dayOfWeek": 2, "quantity": 8 },
    { "dayOfWeek": 3, "quantity": 8 },
    { "dayOfWeek": 4, "quantity": 10 },
    { "dayOfWeek": 5, "quantity": 6 }
  ]
}
```

Rules:

- `dayOfWeek`: `0=Sunday ... 6=Saturday`.
- `quantity`: `0..50`.
- A positive quantity is valid only for days included in `availableDays`.
- Missing days default to `0`.

## Stock calendar

Use this endpoint for the merchant calendar page:

```http
GET /api/v1/merchant/bags/{bagId}/stock-calendar?from=2026-06-27&to=2026-07-11
```

Response rows include:

```json
{
  "dailyStockId": "uuid-or-null",
  "date": "2026-06-28",
  "quantity": 10,
  "reserved": 0,
  "sold": 0,
  "available": 10,
  "status": "ACTIVE",
  "source": "WEEKLY_DEFAULT"
}
```

`source=MANUAL` means the merchant overrode that date with
`PUT /api/v1/merchant/bags/{bagId}/stock/{date}`.

## Merchant notifications

At 20:00 Asia/Ho_Chi_Minh, backend creates forecast stock for upcoming days and
sends a store notification:

- If tomorrow has stock: merchant sees how many bags are already open.
- If tomorrow has no stock: merchant is reminded to open stock.
- Deep link: `/merchant/stores/{storeId}/stock-calendar?date=YYYY-MM-DD`.

When today's stock reaches `2`, `1`, or `0` before pickup end time, backend sends
an add-more reminder:

- Deep link: `/merchant/bags/{bagId}/stock/today`.
- FE can call `PATCH /api/v1/merchant/bags/{bagId}/stock/today` with `delta`.
