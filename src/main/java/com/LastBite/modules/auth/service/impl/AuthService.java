package com.LastBite.modules.auth.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.service.EmailService;
import com.LastBite.modules.auth.dto.request.LoginRequest;
import com.LastBite.modules.auth.dto.request.StoreLoginRequest;
import com.LastBite.modules.auth.dto.request.InitialPasswordChangeRequest;
import com.LastBite.modules.auth.dto.request.RegisterPartnerRequest;
import com.LastBite.modules.auth.dto.request.RegisterRequest;
import com.LastBite.modules.auth.dto.request.ResendOtpRequest;
import com.LastBite.modules.auth.dto.request.VerifyEmailRequest;
import com.LastBite.modules.auth.dto.response.AuthResponse;
import com.LastBite.modules.auth.dto.response.UserResponse;
import com.LastBite.modules.auth.entity.EmailVerificationToken;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.AuthProvider;
import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.auth.repository.EmailVerificationTokenRepository;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.auth.service.AuthServicePort;
import com.LastBite.modules.auth.service.JwtServicePort;
import com.LastBite.modules.store.dto.request.CreateStoreRequest;
import com.LastBite.modules.store.service.StoreServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthServicePort {

    private final UserRepository userRepository;
    private final JwtServicePort jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final StoreServicePort storeService;
    private final EmailVerificationTokenRepository emailTokenRepository;
    private final EmailService emailService;
    private final RoleAssignmentService roleAssignmentService;
    private final MerchantStoreMemberRepository storeMemberRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int VERIFICATION_LINK_EXPIRY_HOURS = 24;

    @Value("${app.auth.email-verification-url:http://localhost:8080/api/v1/auth/verify-email-link}")
    private String emailVerificationUrl;

    /**
     * Đăng ký tài khoản CUSTOMER mới bằng email + mật khẩu.
     * <p>
     * Không trả token ngay — người dùng cần xác minh email bằng link trước.
     */
    @Transactional
    public void register(RegisterRequest request) {
        // Kiểm tra dữ liệu trùng
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new ApiException(ErrorCode.EMAIL_EXISTS);
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new ApiException(ErrorCode.PHONE_EXISTS);
        }

        // Tạo người dùng (emailVerified = false)
        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .accountType(AccountType.PLATFORM)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
        roleAssignmentService.addPlatformRole(user, UserRole.CUSTOMER);

        user = userRepository.save(user);
        log.info("Người dùng mới đã đăng ký: {} ({}) — đang chờ xác minh bằng OTP", user.getEmail(), user.getId());

        sendOtp(user);
    }

    @Transactional
    public void registerMerchant(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new ApiException(ErrorCode.EMAIL_EXISTS);
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new ApiException(ErrorCode.PHONE_EXISTS);
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .accountType(AccountType.PLATFORM)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
        roleAssignmentService.addPlatformRole(user, UserRole.MERCHANT_OWNER);

        user = userRepository.save(user);
        log.info("Chủ cửa hàng mới đã đăng ký account-only: {} ({})", user.getEmail(), user.getId());
        sendVerificationLink(user);
    }

    /**
     * Đăng ký tài khoản STORE_OWNER mới và tạo cửa hàng trong một bước.
     * <p>
     * Không trả token ngay — người dùng cần xác minh email bằng link trước.
     */
    @Transactional
    public void registerPartner(RegisterPartnerRequest request) {
        // Kiểm tra dữ liệu trùng
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new ApiException(ErrorCode.EMAIL_EXISTS);
        }
        if (request.getOwnerPhone() != null && userRepository.existsByPhone(request.getOwnerPhone())) {
            throw new ApiException(ErrorCode.PHONE_EXISTS);
        }

        // 1. Tạo người dùng với role STORE_OWNER
        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getOwnerPhone())
                .accountType(AccountType.PLATFORM)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
        roleAssignmentService.addPlatformRole(user, UserRole.MERCHANT_OWNER);

        user = userRepository.save(user);

        // 2. Tạo cửa hàng cho chủ cửa hàng này
        CreateStoreRequest storeReq = new CreateStoreRequest();
        storeReq.setName(request.getStoreName());
        storeReq.setDescription(request.getStoreDescription());
        storeReq.setCategory(request.getStoreCategory());
        storeReq.setPhone(request.getStorePhone());
        storeReq.setEmail(request.getStoreEmail());
        storeReq.setAddress(request.getStoreAddress());
        storeReq.setDistrict(request.getStoreDistrict());
        storeReq.setCity(request.getStoreCity());
        storeReq.setLat(request.getLat());
        storeReq.setLng(request.getLng());
        storeReq.setCoverImageUrl(request.getCoverImageUrl());
        storeReq.setLogoUrl(request.getLogoUrl());
        storeReq.setBusinessLicenseNumber(request.getBusinessLicenseNumber());
        storeReq.setBusinessLicenseImageUrl(request.getBusinessLicenseImageUrl());

        storeService.createStoreInternal(user, storeReq);

        log.info("Đối tác mới đã đăng ký: {} ({}) với cửa hàng: {} — đang chờ xác minh bằng OTP",
                user.getEmail(), user.getId(), request.getStoreName());

        sendOtp(user);
    }

    /**
     * Xác minh email bằng mã OTP.
     * <p>
     * Khi thành công: đánh dấu emailVerified, trả access token và cấp refresh cookie.
     */
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        EmailVerificationToken token = emailTokenRepository
                .findLatestValidOtpByUserId(user.getId(), Instant.now())
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_EXPIRED));

        if (token.hasMaxAttempts()) {
            throw new ApiException(ErrorCode.OTP_MAX_ATTEMPTS);
        }

        if (!token.getOtpCode().equals(request.getOtpCode())) {
            token.setAttempts(token.getAttempts() + 1);
            emailTokenRepository.save(token);
            throw new ApiException(ErrorCode.OTP_INVALID);
        }

        // OTP chính xác — đánh dấu đã xác minh
        token.setVerified(true);
        emailTokenRepository.save(token);

        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email đã được xác minh cho người dùng: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    /**
     * Xác minh email bằng token link một lần.
     * <p>
     * Đây là luồng xác minh đăng ký. OTP vẫn được giữ cho các luồng rủi ro cao
     * riêng biệt, nơi nhập mã ngắn hạn là lựa chọn phù hợp hơn.
     */
    @Transactional
    public AuthResponse verifyEmailLink(String rawToken) {
        String tokenHash = jwtService.hashToken(rawToken);
        EmailVerificationToken token = emailTokenRepository
                .findValidLinkByTokenHash(tokenHash, Instant.now())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID,
                        "Link xác minh email không hợp lệ hoặc đã hết hạn"));

        User user = token.getUser();
        if (user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        token.setVerified(true);
        emailTokenRepository.save(token);

        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email đã được xác minh bằng link cho người dùng: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    /**
     * Gửi lại OTP tới email người dùng. Xóa token chưa xác minh trước đó trước.
     */
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        sendOtp(user);
    }

    /**
     * Gửi lại link xác minh đăng ký.
     */
    @Transactional
    public void resendVerificationLink(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        sendVerificationLink(user);
    }

    /**
     * Đăng nhập bằng email + mật khẩu.
     * <p>
     * Từ chối đăng nhập nếu email chưa được xác minh (chỉ áp dụng user LOCAL).
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getAccountType() != AccountType.PLATFORM) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Kiểm tra mật khẩu (chỉ với tài khoản LOCAL)
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Kiểm tra trạng thái tài khoản
        if (user.getStatus() == UserStatus.BANNED) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        // Kiểm tra xác minh email (tài khoản LOCAL bắt buộc xác minh email)
        if (user.getAuthProvider() == AuthProvider.LOCAL && !user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        log.info("Người dùng đã đăng nhập: {}", user.getEmail());
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse storeLogin(StoreLoginRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.getUsername().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getAccountType() != AccountType.STORE_MEMBER
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        var membership = storeMemberRepository.findByUserId(user.getId())
                .filter(item -> item.getStatus() == StoreMemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Tài khoản không còn quyền tại cửa hàng"));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return buildStoreMemberAuthResponse(user, membership.getRole().getCode().name(), membership.getStore().getId());
    }

    @Transactional
    public void changeInitialPassword(UUID userId, InitialPasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getAccountType() != AccountType.STORE_MEMBER || !user.isMustChangePassword()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Tài khoản không ở trạng thái đổi mật khẩu lần đầu");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenService.revokeAllByUserId(userId);
    }

    /**
     * Làm mới access token bằng refresh token hợp lệ.
     * Áp dụng xoay vòng token: thu hồi token cũ và cấp token mới.
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        // 1. Xác thực refresh token cũ
        // Nếu token đã bị thu hồi nhưng vẫn được dùng lại, toàn bộ phiên sẽ bị hủy tự động
        UUID userId = refreshTokenService.validateAndGetUserId(rawRefreshToken);

        // 2. Thu hồi token cũ (xoay vòng token)
        refreshTokenService.revokeToken(rawRefreshToken);

        // 3. Tải người dùng và cấp token mới
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        log.debug("Token đã được làm mới cho người dùng: {}", user.getEmail());
        if (user.getAccountType() == AccountType.STORE_MEMBER) {
            var membership = storeMemberRepository.findByUserId(user.getId())
                    .filter(item -> item.getStatus() == StoreMemberStatus.ACTIVE)
                    .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
            return buildStoreMemberAuthResponse(user, membership.getRole().getCode().name(), membership.getStore().getId());
        }
        return buildAuthResponse(user);
    }

    /**
     * Đăng xuất — thu hồi refresh token cụ thể.
     */
    @Transactional
    public void logout(UUID userId, UUID sessionId) {
        refreshTokenService.revokeSession(userId, sessionId);
    }

    /**
     * Đăng xuất khỏi TẤT CẢ thiết bị — thu hồi toàn bộ refresh token.
     */
    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokenService.revokeAllByUserId(userId);
    }

    /**
     * Lấy hồ sơ người dùng hiện tại từ claim trong JWT.
     */
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return toUserResponse(user);
    }

    // ── Private helpers ──────────────────────────────────────

    private void sendOtp(User user) {
        // Xóa mọi token chưa xác minh trước đó
        emailTokenRepository.deleteUnverifiedByUserId(user.getId());

        // Tạo OTP 6 chữ số
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .otpCode(otp)
                .expiresAt(Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60L))
                .build();
        emailTokenRepository.save(token);

        // Gửi email bất đồng bộ
        emailService.sendOtpEmail(user.getEmail(), user.getFullName(), otp);
    }

    private void sendVerificationLink(User user) {
        emailTokenRepository.deleteUnverifiedByUserId(user.getId());

        String rawToken = jwtService.generateRefreshToken();
        String tokenHash = jwtService.hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(VERIFICATION_LINK_EXPIRY_HOURS * 3600L))
                .build();
        emailTokenRepository.save(token);

        String separator = emailVerificationUrl.contains("?") ? "&" : "?";
        String link = emailVerificationUrl + separator + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        emailService.sendVerificationLinkEmail(user.getEmail(), user.getFullName(), link);
    }

    private AuthResponse buildAuthResponse(User user) {
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        String accessToken = jwtService.generateAccessToken(user, refreshToken.sessionId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.rawToken())
                .expiresIn(jwtService.getAccessTokenDuration())
                .build();
    }

    private AuthResponse buildStoreMemberAuthResponse(User user, String role, UUID storeId) {
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        String accessToken = jwtService.generateAccessToken(
                user, refreshToken.sessionId(), java.util.List.of(role), storeId);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.rawToken())
                .expiresIn(jwtService.getAccessTokenDuration())
                .build();
    }

    private UserResponse toUserResponse(User user) {
        java.util.List<String> roles = user.getRoles().stream()
                .map(role -> role.getCode().name())
                .sorted()
                .toList();
        if (user.getAccountType() == AccountType.STORE_MEMBER) {
            roles = storeMemberRepository.findByUserId(user.getId())
                    .filter(member -> member.getStatus() == StoreMemberStatus.ACTIVE)
                    .map(member -> java.util.List.of(member.getRole().getCode().name()))
                    .orElseGet(java.util.List::of);
        }
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .roles(roles)
                .accountType(user.getAccountType())
                .username(user.getUsername())
                .mustChangePassword(user.isMustChangePassword())
                .status(user.getStatus())
                .authProvider(user.getAuthProvider())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
