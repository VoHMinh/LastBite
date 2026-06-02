package com.LastBite.modules.user.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.dto.response.UserResponse;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.user.dto.request.ChangePasswordRequest;
import com.LastBite.modules.user.dto.request.UpdateProfileRequest;
import com.LastBite.modules.user.service.UserServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserServicePort {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Lấy hồ sơ người dùng (cache Redis 30 phút).
     */
    @Cacheable(value = "user-profile", key = "#userId")
    public UserResponse getProfile(UUID userId) {
        User user = findUserOrThrow(userId);
        return toUserResponse(user);
    }

    /**
     * Cập nhật hồ sơ người dùng — xóa cache liên quan.
     */
    @Transactional
    @CacheEvict(value = "user-profile", key = "#userId")
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            // Kiểm tra số điện thoại không bị trùng
            if (!request.getPhone().isBlank()
                    && !request.getPhone().equals(user.getPhone())
                    && userRepository.existsByPhone(request.getPhone())) {
                throw new ApiException(ErrorCode.PHONE_EXISTS);
            }
            user.setPhone(request.getPhone().isBlank() ? null : request.getPhone());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().isBlank() ? null : request.getAvatarUrl());
        }

        user = userRepository.save(user);
        log.info("Đã cập nhật hồ sơ cho người dùng: {}", user.getEmail());
        return toUserResponse(user);
    }

    /**
     * Đổi mật khẩu — xóa cache liên quan.
     */
    @Transactional
    @CacheEvict(value = "user-profile", key = "#userId")
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUserOrThrow(userId);

        if (user.getPasswordHash() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Tài khoản OAuth không có mật khẩu để thay đổi");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Mật khẩu cũ không đúng");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Đã đổi mật khẩu cho người dùng: {}", user.getEmail());
    }

    // ── Helpers ──

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .authProvider(user.getAuthProvider())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
