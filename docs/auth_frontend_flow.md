# LastBite Auth Flow For Frontend

Tài liệu này mô tả luồng auth FE cần làm với backend LastBite sau khi refresh token được chuyển sang cơ chế HttpOnly cookie.

## 1. Nguyên tắc chung

- Backend base URL local: `http://localhost:8080`
- FE local đang được CORS cho phép: `http://localhost:3000`, `http://localhost:5173`
- Mọi response được bọc trong `ApiResponse`:

```json
{
  "code": 1000,
  "message": "Success",
  "result": {},
  "timestamp": "2026-06-05T..."
}
```

- FE lấy access token tại `response.result.access_token`.
- FE không lấy refresh token từ JSON body.
- Refresh token nằm trong cookie tên `refresh_token`, do backend set qua header `Set-Cookie`.
- Cookie này là `HttpOnly`, nên JavaScript không đọc được bằng `document.cookie`. Đây là đúng thiết kế bảo mật.
- Khi gọi các API cần cookie như `/auth/refresh`, FE phải bật gửi credential:
  - `fetch`: dùng `credentials: "include"`
  - Axios: dùng `withCredentials: true`
- Khi gọi API protected, FE gửi access token trong header:

```http
Authorization: Bearer <access_token>
```

## 2. Cấu hình HTTP client FE

Ví dụ với Axios:

```ts
import axios from "axios";

export const api = axios.create({
  baseURL: "http://localhost:8080",
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const accessToken = authStore.getAccessToken();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});
```

Ví dụ với `fetch`:

```ts
await fetch("http://localhost:8080/api/v1/auth/refresh", {
  method: "POST",
  credentials: "include",
});
```

## 3. Luồng đăng ký khách hàng

FE gọi:

```http
POST /api/v1/auth/register
Content-Type: application/json
```

Body:

```json
{
  "email": "customer@example.com",
  "password": "Password@123",
  "fullName": "Nguyen Van A",
  "phone": "0900000000"
}
```

Kết quả thành công:

- HTTP `201`
- `code = 1000`
- Backend gửi email xác minh.
- User chưa được login ngay.
- FE nên chuyển sang màn hình "Kiểm tra email để xác minh tài khoản".

## 4. Luồng đăng ký đối tác

FE gọi:

```http
POST /api/v1/auth/register-partner
Content-Type: application/json
```

Body tối thiểu:

```json
{
  "email": "merchant@example.com",
  "password": "Password@123",
  "fullName": "Tran Van B",
  "ownerPhone": "0900000001",
  "storeName": "Tiệm bánh Test",
  "storeCategory": "BAKERY",
  "storeAddress": "123 Nguyen Trai",
  "storeDistrict": "District 1",
  "storeCity": "Ho Chi Minh City"
}
```

Kết quả thành công:

- HTTP `201`
- Backend tạo user role `STORE_OWNER`, tạo store, rồi gửi email xác minh.
- User chưa được login ngay.
- FE chuyển sang màn hình "Kiểm tra email để xác minh tài khoản".

## 5. Luồng xác minh email bằng link

Backend gửi email có link xác minh. Có 2 cách triển khai UX:

### Cách khuyến nghị

Email link trỏ về FE:

```text
http://localhost:5173/verify-email?token=<token>
```

FE đọc `token` từ URL, rồi gọi backend:

```http
GET /api/v1/auth/verify-email-link?token=<token>
```

Kết quả thành công:

- Backend đánh dấu email đã xác minh.
- Backend trả `result.access_token`.
- Backend set cookie `refresh_token`.
- FE lưu access token vào auth state.
- FE chuyển user vào app hoặc về trang hoàn tất xác minh.

### Cách hiện tại nếu email link trỏ thẳng BE

Nếu email link là:

```text
http://localhost:8080/api/v1/auth/verify-email-link?token=<token>
```

Browser sẽ hiện JSON thô từ backend. Cách này chạy được về mặt API nhưng UX không tốt. FE nên đổi link email sang URL FE như cách khuyến nghị.

## 6. Luồng xác minh email bằng OTP

FE gọi:

```http
POST /api/v1/auth/verify-email
Content-Type: application/json
```

Body:

```json
{
  "email": "customer@example.com",
  "otpCode": "123456"
}
```

Kết quả thành công giống verify link:

- FE lấy `result.access_token`.
- Backend set cookie `refresh_token`.
- FE xem user là đã đăng nhập.

## 7. Luồng đăng nhập

FE gọi:

```http
POST /api/v1/auth/login
Content-Type: application/json
```

Body:

```json
{
  "email": "customer@example.com",
  "password": "Password@123"
}
```

Response mẫu:

```json
{
  "code": 1000,
  "message": "Đăng nhập thành công",
  "result": {
    "access_token": "eyJ...",
    "token_type": "Bearer",
    "expires_in": 900
  },
  "timestamp": "2026-06-05T..."
}
```

FE cần làm:

1. Lưu `result.access_token` vào memory hoặc state management.
2. Không tìm `refresh_token` trong body.
3. Để browser tự lưu cookie `refresh_token` từ `Set-Cookie`.
4. Gọi `/api/v1/auth/me` để lấy profile hiện tại nếu cần hydrate user state.

Không nên lưu access token lâu dài trong localStorage nếu không cần. Tốt nhất là giữ trong memory/state; khi reload trang thì gọi refresh để lấy access token mới.

## 8. Luồng gọi API protected

Với mọi API cần đăng nhập:

```http
Authorization: Bearer <access_token>
```

Ví dụ:

```ts
const res = await api.get("/api/v1/auth/me");
```

Nếu token hợp lệ và session chưa logout, backend trả user hiện tại.

## 9. Luồng refresh token

Khi access token hết hạn hoặc FE bị reload mất access token, FE gọi:

```http
POST /api/v1/auth/refresh
```

Không gửi body.

Bắt buộc gửi cookie:

```ts
const res = await api.post("/api/v1/auth/refresh");
const newAccessToken = res.data.result.access_token;
authStore.setAccessToken(newAccessToken);
```

Backend sẽ:

- Đọc `refresh_token` từ HttpOnly cookie.
- Revoke refresh token cũ.
- Tạo refresh token mới và set cookie mới.
- Trả access token mới trong `result.access_token`.

## 10. Auto refresh khi gặp 401

Khi một API protected trả 401:

1. FE gọi `POST /api/v1/auth/refresh`.
2. Nếu refresh thành công, lưu access token mới.
3. Retry request ban đầu một lần.
4. Nếu refresh cũng 401, clear auth state và đưa user về login.

Pseudo flow:

```ts
async function handle401(originalRequest) {
  try {
    const refreshRes = await api.post("/api/v1/auth/refresh");
    authStore.setAccessToken(refreshRes.data.result.access_token);
    return api(originalRequest);
  } catch {
    authStore.clear();
    router.navigate("/login");
  }
}
```

Không retry vô hạn. Chỉ retry request ban đầu một lần.

## 11. Luồng lấy user hiện tại

FE gọi:

```http
GET /api/v1/auth/me
Authorization: Bearer <access_token>
```

Dùng endpoint này để:

- Hydrate user sau login/refresh.
- Check role: `CUSTOMER`, `STORE_OWNER`, `ADMIN`.
- Guard route cần đăng nhập.

Nếu trả 401:

- Access token sai, hết hạn, hoặc session đã bị logout.
- FE thử refresh một lần.
- Nếu refresh fail thì chuyển về login.

## 12. Luồng logout phiên hiện tại

FE gọi:

```http
POST /api/v1/auth/logout
Authorization: Bearer <access_token>
```

Không gửi body.

Backend sẽ:

- Revoke session hiện tại.
- Clear cookie `refresh_token`.
- Access token hiện tại mất hiệu lực ngay.

FE cần làm sau khi logout thành công:

```ts
authStore.clearAccessToken();
router.navigate("/login");
```

Sau logout, nếu gọi `/api/v1/auth/me` bằng access token cũ thì backend sẽ trả 401.

## 13. Luồng logout tất cả thiết bị

FE gọi:

```http
POST /api/v1/auth/logout-all
Authorization: Bearer <access_token>
```

Không gửi body.

Backend sẽ:

- Revoke mọi session của user hiện tại.
- Clear cookie `refresh_token` trên browser hiện tại.
- Tất cả access token cũ của user đó mất hiệu lực ngay.

API này là "user tự đăng xuất khỏi tất cả thiết bị của mình", không phải admin logout toàn hệ thống.

## 14. Google login

FE dùng Google Sign-In để lấy `id_token`, rồi gọi:

```http
POST /api/v1/auth/google
Content-Type: application/json
```

Body:

```json
{
  "idToken": "<google_id_token>"
}
```

Kết quả thành công giống login thường:

- FE lấy `result.access_token`.
- Backend set cookie `refresh_token`.
- FE gọi `/auth/me` nếu cần profile đầy đủ.

## 15. Error handling FE cần biết

Một số mã thường gặp:

- `1000`: thành công.
- `4010`: chưa xác thực hoặc access token không hợp lệ.
- `4012`: token hết hạn.
- `4013`: token không hợp lệ, refresh token thiếu hoặc sai.
- `4033`: email đã được xác minh.
- `4034`: email chưa xác minh.
- `4091`: email đã được đăng ký.
- `4000`: lỗi validation, xem `errors`.
- `5000`: lỗi hệ thống.

Với validation error, response có thể có:

```json
{
  "code": 4000,
  "message": "Dữ liệu không hợp lệ",
  "errors": {
    "email": "Email không đúng định dạng"
  }
}
```

## 16. Checklist cho FE

- Login/register/verify gọi đúng endpoint.
- FE đọc payload bằng `response.result`, không dùng `response.data` nếu đang nói về JSON backend.
- Access token lấy từ `result.access_token`.
- Refresh token không đọc và không lưu thủ công.
- HTTP client bật `withCredentials: true` hoặc `credentials: "include"`.
- Protected API luôn gửi `Authorization: Bearer <access_token>`.
- Khi reload app, thử gọi `/auth/refresh` để lấy access token mới.
- Khi 401, thử refresh một lần rồi retry request.
- Khi logout/logout-all, clear access token local và redirect login.
- Không gửi `refreshToken` trong body cho `/auth/refresh`, `/auth/logout`, `/auth/logout-all`.
