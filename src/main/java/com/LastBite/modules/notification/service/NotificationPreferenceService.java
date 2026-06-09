package com.LastBite.modules.notification.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.LastBite.modules.notification.dto.response.NotificationPreferenceResponse;
import com.LastBite.modules.notification.entity.NotificationPreference;
import com.LastBite.modules.notification.enums.NotificationCategory;
import com.LastBite.modules.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> list(UUID userId) {
        return Arrays.stream(NotificationCategory.values())
                .map(category -> preferenceRepository.findByUserIdAndCategory(userId, category)
                        .map(this::toResponse)
                        .orElseGet(() -> defaultResponse(category)))
                .toList();
    }

    @Transactional
    public NotificationPreferenceResponse update(UUID userId, UpdateNotificationPreferenceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        NotificationPreference preference = preferenceRepository
                .findByUserIdAndCategory(userId, request.getCategory())
                .orElseGet(() -> NotificationPreference.builder()
                        .user(user)
                        .category(request.getCategory())
                        .pushEnabled(true)
                        .emailEnabled(false)
                        .build());

        if (request.getPushEnabled() != null) {
            preference.setPushEnabled(request.getPushEnabled());
        }
        if (request.getEmailEnabled() != null) {
            preference.setEmailEnabled(request.getEmailEnabled());
        }
        return toResponse(preferenceRepository.save(preference));
    }

    @Transactional(readOnly = true)
    public boolean isPushEnabled(UUID userId, NotificationCategory category) {
        return preferenceRepository.findByUserIdAndCategory(userId, category)
                .map(NotificationPreference::isPushEnabled)
                .orElse(true);
    }

    private NotificationPreferenceResponse defaultResponse(NotificationCategory category) {
        return NotificationPreferenceResponse.builder()
                .category(category)
                .pushEnabled(true)
                .emailEnabled(false)
                .build();
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return NotificationPreferenceResponse.builder()
                .category(preference.getCategory())
                .pushEnabled(preference.isPushEnabled())
                .emailEnabled(preference.isEmailEnabled())
                .build();
    }
}
