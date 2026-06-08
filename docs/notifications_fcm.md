# LastBite Notifications + Firebase Cloud Messaging

This backend now supports:

- In-app notification inbox for web/mobile.
- FCM push for `WEB`, `ANDROID`, and `IOS` device tokens.
- User notification preferences.
- Delivery logging for FCM attempts.
- Scheduled reminders for payment expiry and pickup windows.

## Backend Environment Variables

| Variable | Required | Example | Notes |
| --- | --- | --- | --- |
| `APP_FCM_ENABLED` | Yes in staging/prod | `true` | Keep `false` for local dev if Firebase is not configured. |
| `APP_FCM_CREDENTIALS_PATH` | One of path/base64 | `D:/secrets/lastbite-firebase-admin.json` | Absolute path to Firebase service account JSON. Do not commit this file. |
| `APP_FCM_CREDENTIALS_BASE64` | One of path/base64 | `eyJ0eXAiOi...` | Base64 encoded service account JSON. Good for Docker/cloud env vars. |
| `APP_NOTIFICATIONS_REMINDER_FIXED_DELAY_MS` | No | `60000` | Optional scheduler delay. Defaults to 60 seconds. |

Use only one credential source. `APP_FCM_CREDENTIALS_BASE64` has priority over `APP_FCM_CREDENTIALS_PATH`.

## Firebase Console Setup

1. Create or open a Firebase project for LastBite.
2. Go to Project settings -> Service accounts.
3. Generate a new private key for Firebase Admin SDK.
4. Save the JSON outside the repository.
5. Configure backend env:
   - Local/staging file path: set `APP_FCM_CREDENTIALS_PATH`.
   - Docker/cloud: base64 encode the JSON and set `APP_FCM_CREDENTIALS_BASE64`.
6. Enable Cloud Messaging in the project.
7. Register client apps:
   - Android app: add package name, download `google-services.json`.
   - iOS app: add bundle id, download `GoogleService-Info.plist`, configure APNs key/certificate.
   - Web app: create web app config and generate/use Web Push certificate VAPID key.

PowerShell base64 command:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\secrets\lastbite-firebase-admin.json"))
```

## Client Integration Flow

Every logged-in client should:

1. Ask user permission to receive notifications.
2. Get FCM registration token from Firebase SDK.
3. Call:

```http
POST /api/v1/notifications/devices
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "deviceToken": "<fcm-token>",
  "deviceType": "WEB",
  "appVersion": "1.0.0"
}
```

Use `ANDROID` or `IOS` for native apps.

On logout from one device:

```http
DELETE /api/v1/notifications/devices/{deviceId}
```

On logout-all:

```http
DELETE /api/v1/notifications/devices
```

## Web Push Notes

For web, the frontend needs a Firebase messaging service worker, usually:

```text
public/firebase-messaging-sw.js
```

The web app must initialize Firebase with the web app config and request a token using the Firebase Web Push/VAPID key. After receiving the token, register it through the backend endpoint above.

## Mobile Push Notes

Android:

- Add `google-services.json`.
- Add Firebase Messaging SDK.
- Request notification permission on Android 13+.
- Send the FCM token to the backend after login and whenever Firebase refreshes it.

iOS:

- Add `GoogleService-Info.plist`.
- Enable Push Notifications and Background Modes in Xcode.
- Configure APNs key/certificate in Firebase Console.
- Request notification permission.
- Send the FCM token to the backend after login and token refresh.

## Notification APIs

| Purpose | Endpoint |
| --- | --- |
| Register device token | `POST /api/v1/notifications/devices` |
| List active device tokens | `GET /api/v1/notifications/devices` |
| Deactivate one device | `DELETE /api/v1/notifications/devices/{deviceId}` |
| Deactivate all devices | `DELETE /api/v1/notifications/devices` |
| List inbox | `GET /api/v1/notifications?page=0&size=20` |
| List unread | `GET /api/v1/notifications/unread` |
| Unread count | `GET /api/v1/notifications/unread-count` |
| Mark one read | `PATCH /api/v1/notifications/{notificationId}/read` |
| Mark all read | `PATCH /api/v1/notifications/read-all` |
| Delete notification | `DELETE /api/v1/notifications/{notificationId}` |
| List preferences | `GET /api/v1/notifications/preferences` |
| Update preference | `PUT /api/v1/notifications/preferences` |
| Admin broadcast | `POST /api/v1/notifications/admin/broadcast` |
| Admin stats | `GET /api/v1/notifications/admin/stats` |

## Implemented Backend Triggers

| Trigger | Notification type | Notes |
| --- | --- | --- |
| Customer creates reservation order | `ORDER_RESERVED` | Current order flow is still `PENDING_PAYMENT`. |
| Pending payment nearly expires | `PAYMENT_EXPIRING` | Scheduler checks orders expiring within 3 minutes. |
| Pickup starts soon | `PICKUP_REMINDER_60M`, `PICKUP_REMINDER_30M`, `PICKUP_REMINDER_10M` | Applies to `PAID` and `READY_FOR_PICKUP`. |
| Pickup window opens | `PICKUP_WINDOW_OPEN` | Deduped per order. |
| Pickup window ends soon | `PICKUP_ENDING_SOON` | 15-minute threshold. |
| Merchant sets/restocks today's bag from 0 to available | `FAVORITE_STORE_STOCK_AVAILABLE` | Sent to users who favorited that store. |
| Admin campaign | caller-provided type | Creates inbox + push for active customers. |

## Next Implementation Plan

| Phase | Work |
| --- | --- |
| Payment lifecycle | Add payment webhook/status transition to `PAID`, then trigger `PAYMENT_SUCCESS` and merchant `MERCHANT_NEW_PAID_ORDER`. |
| Reservation expiry | Add job that marks overdue `PENDING_PAYMENT` as `EXPIRED`, releases reserved stock, and sends `ORDER_EXPIRED`. |
| Merchant order management | Add store-owner order list, mark ready, confirm pickup by pickup code, then trigger `ORDER_READY_FOR_PICKUP`, `ORDER_PICKED_UP`. |
| Flash sale targeting | Extend admin broadcast with filters: district, category, favorite store, recently viewed, distance from default address. |
| Email fallback | Add email templates for payment success, cancellation, refund, and critical pickup/order changes. |
| Realtime badge | Optional SSE/WebSocket only for live unread badge updates. Keep FCM + inbox as the durable core. |
| Observability | Add metrics for active tokens, sent/failed/skipped delivery counts, invalid-token cleanup, and scheduler counts. |

## Important Security Rules

- Never commit Firebase service account JSON.
- Keep `APP_FCM_ENABLED=false` unless credentials are present.
- Store only masked device token in API responses.
- Token invalidation is automatic when FCM returns invalid-token errors.
- Marketing/promotion push can be disabled through preferences; transactional inbox notifications are still created.
