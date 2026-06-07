package com.LastBite.modules.notification.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.notification.dto.request.RegisterNotificationDeviceRequest;
import com.LastBite.modules.notification.dto.response.NotificationDeviceResponse;
import com.LastBite.modules.notification.entity.NotificationDevice;
import com.LastBite.modules.notification.repository.NotificationDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeviceService {

    private final NotificationDeviceRepository deviceRepository;
    private final UserRepository userRepository;

    @Transactional
    public NotificationDeviceResponse register(UUID userId, RegisterNotificationDeviceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        String token = request.getDeviceToken().trim();

        var existing = deviceRepository.findByUserIdAndDeviceToken(userId, token);
        if (existing.isPresent()) {
            NotificationDevice device = existing.get();
            device.setActive(true);
            device.setDeviceType(request.getDeviceType());
            device.setAppVersion(trimToNull(request.getAppVersion()));
            device.setLastSeenAt(Instant.now());
            return toResponse(deviceRepository.save(device));
        }

        int reassigned = deviceRepository.deactivateTokenForOtherUsers(token, userId);
        if (reassigned > 0) {
            log.info("Deactivated {} duplicate FCM token(s) for other users", reassigned);
        }

        NotificationDevice device = NotificationDevice.builder()
                .user(user)
                .deviceToken(token)
                .deviceType(request.getDeviceType())
                .appVersion(trimToNull(request.getAppVersion()))
                .active(true)
                .lastSeenAt(Instant.now())
                .build();
        return toResponse(deviceRepository.save(device));
    }

    @Transactional(readOnly = true)
    public List<NotificationDeviceResponse> listActive(UUID userId) {
        return deviceRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deactivate(UUID userId, UUID deviceId) {
        NotificationDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!device.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        device.setActive(false);
        deviceRepository.save(device);
    }

    @Transactional
    public void deactivateAll(UUID userId) {
        deviceRepository.deactivateAllByUserId(userId);
    }

    @Transactional
    public void deactivateToken(String token) {
        deviceRepository.deactivateByToken(token);
    }

    private NotificationDeviceResponse toResponse(NotificationDevice device) {
        return NotificationDeviceResponse.builder()
                .id(device.getId())
                .deviceToken(maskToken(device.getDeviceToken()))
                .deviceType(device.getDeviceType())
                .appVersion(device.getAppVersion())
                .active(device.isActive())
                .lastSeenAt(device.getLastSeenAt())
                .createdAt(device.getCreatedAt())
                .build();
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 16) {
            return token;
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 8);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
