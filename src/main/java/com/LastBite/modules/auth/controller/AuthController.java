package com.LastBite.modules.auth.controller;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.auth.dto.request.GoogleAuthRequest;
import com.LastBite.modules.auth.dto.request.LoginRequest;
import com.LastBite.modules.auth.dto.request.RefreshTokenRequest;
import com.LastBite.modules.auth.dto.request.RegisterPartnerRequest;
import com.LastBite.modules.auth.dto.request.RegisterRequest;
import com.LastBite.modules.auth.dto.request.ResendOtpRequest;
import com.LastBite.modules.auth.dto.request.VerifyEmailRequest;
import com.LastBite.modules.auth.dto.response.AuthResponse;
import com.LastBite.modules.auth.dto.response.UserResponse;
import com.LastBite.modules.auth.service.AuthServicePort;
import com.LastBite.modules.auth.service.GoogleAuthServicePort;
import com.LastBite.modules.auth.service.JwtServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Xác thực", description = "Đăng ký, đăng nhập, xác minh email, Google OAuth và quản lý token")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AuthServicePort authService;
    private final GoogleAuthServicePort googleAuthService;
    private final JwtServicePort jwtService;

    @Value("${app.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.auth.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản khách hàng và gửi link xác minh email")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(null, "Đăng ký thành công - vui lòng kiểm tra email để xác minh tài khoản"));
    }

    @PostMapping("/register-partner")
    @Operation(summary = "Đăng ký tài khoản đối tác, tạo cửa hàng và gửi link xác minh email")
    public ResponseEntity<ApiResponse<Void>> registerPartner(@Valid @RequestBody RegisterPartnerRequest request) {
        authService.registerPartner(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(null, "Đăng ký đối tác thành công - vui lòng kiểm tra email để xác minh tài khoản"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Xác minh email bằng OTP; dành cho các luồng rủi ro cao")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authResponse(authService.verifyEmail(request), "Xác minh email thành công");
    }

    @GetMapping("/verify-email-link")
    @Operation(summary = "Xác minh email bằng link một lần; dùng cho luồng đăng ký")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmailLink(@RequestParam String token) {
        return authResponse(authService.verifyEmailLink(token), "Xác minh email thành công");
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Gửi lại mã OTP xác minh")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã gửi lại mã OTP - vui lòng kiểm tra email"));
    }

    @PostMapping("/resend-verification-link")
    @Operation(summary = "Gửi lại link xác minh email")
    public ResponseEntity<ApiResponse<Void>> resendVerificationLink(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendVerificationLink(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã gửi lại link xác minh - vui lòng kiểm tra email"));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng email và mật khẩu")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return authResponse(authService.login(request), "Đăng nhập thành công");
    }

    @PostMapping("/google")
    @Operation(summary = "Đăng nhập hoặc đăng ký bằng Google id_token")
    public ResponseEntity<ApiResponse<AuthResponse>> googleAuth(@Valid @RequestBody GoogleAuthRequest request) {
        return authResponse(googleAuthService.authenticateWithGoogle(request.getIdToken()),
                "Đăng nhập Google thành công");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới access token bằng refresh cookie httpOnly")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequest request) {
        return authResponse(authService.refresh(resolveRefreshToken(cookieRefreshToken, request)),
                "Làm mới token thành công");
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất và thu hồi refresh token hiện tại")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(resolveRefreshToken(cookieRefreshToken, request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.ok());
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Đăng xuất khỏi tất cả thiết bị")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("user_id"));
        authService.logoutAll(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.ok());
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin người dùng hiện tại")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("user_id"));
        return ResponseEntity.ok(ApiResponse.ok(authService.getCurrentUser(userId)));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> authResponse(AuthResponse auth, String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(auth.getRefreshToken()).toString())
                .body(ApiResponse.ok(auth.withoutRefreshToken(), message));
    }

    private String resolveRefreshToken(String cookieRefreshToken, RefreshTokenRequest request) {
        if (cookieRefreshToken != null && !cookieRefreshToken.isBlank()) {
            return cookieRefreshToken;
        }
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            return request.getRefreshToken();
        }
        throw new ApiException(ErrorCode.TOKEN_INVALID, "Thiếu refresh token");
    }

    private ResponseCookie refreshCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(jwtService.getRefreshTokenDuration()))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
