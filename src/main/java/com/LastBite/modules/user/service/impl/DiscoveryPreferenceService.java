package com.LastBite.modules.user.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.user.dto.request.UpdateDiscoveryPreferenceRequest;
import com.LastBite.modules.user.dto.response.DiscoveryPreferenceResponse;
import com.LastBite.modules.user.entity.UserDiscoveryPreference;
import com.LastBite.modules.user.enums.CollectionTimeSlot;
import com.LastBite.modules.user.enums.DiscoveryOnboardingStatus;
import com.LastBite.modules.user.enums.PreferredDiet;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import com.LastBite.modules.user.service.DiscoveryPreferenceServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscoveryPreferenceService implements DiscoveryPreferenceServicePort {

    private static final double MAX_RADIUS_KM = 50.0;

    private final UserDiscoveryPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    @Override
    public DiscoveryPreferenceResponse get(UUID userId) {
        return preferenceRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    @Override
    @Transactional
    @CacheEvict(value = "bag-discovery", allEntries = true)
    public DiscoveryPreferenceResponse update(UUID userId, UpdateDiscoveryPreferenceRequest request) {
        UserDiscoveryPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> newPreference(userId));

        preference.setPreferredDiet(request.getPreferredDiet() == null
                ? PreferredDiet.NOT_SPECIFIED
                : request.getPreferredDiet());
        preference.setPreferredCollectionTimes(normalizeSlots(request.getPreferredCollectionTimes()));
        applyLocation(preference, request);
        preference.setOnboardingStatus(DiscoveryOnboardingStatus.COMPLETED);

        return toResponse(preferenceRepository.save(preference));
    }

    @Override
    @Transactional
    @CacheEvict(value = "bag-discovery", allEntries = true)
    public DiscoveryPreferenceResponse skipOnboarding(UUID userId) {
        UserDiscoveryPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> newPreference(userId));
        preference.setOnboardingStatus(DiscoveryOnboardingStatus.SKIPPED);
        return toResponse(preferenceRepository.save(preference));
    }

    private UserDiscoveryPreference newPreference(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return UserDiscoveryPreference.builder()
                .user(user)
                .preferredDiet(PreferredDiet.NOT_SPECIFIED)
                .preferredCollectionTimes(new LinkedHashSet<>())
                .onboardingStatus(DiscoveryOnboardingStatus.NOT_STARTED)
                .build();
    }

    private void applyLocation(UserDiscoveryPreference preference, UpdateDiscoveryPreferenceRequest request) {
        boolean hasAnyLocationField = request.getDefaultLocationLabel() != null
                || request.getDefaultLat() != null
                || request.getDefaultLng() != null
                || request.getDefaultRadiusKm() != null;
        if (!hasAnyLocationField) {
            preference.setDefaultLocationLabel(null);
            preference.setDefaultLat(null);
            preference.setDefaultLng(null);
            preference.setDefaultRadiusKm(null);
            return;
        }

        if (request.getDefaultLocationLabel() == null || request.getDefaultLocationLabel().isBlank()
                || request.getDefaultLat() == null
                || request.getDefaultLng() == null
                || request.getDefaultRadiusKm() == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT,
                    "defaultLocationLabel, defaultLat, defaultLng and defaultRadiusKm must be sent together");
        }
        if (request.getDefaultRadiusKm() <= 0 || request.getDefaultRadiusKm() > MAX_RADIUS_KM) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Default radius must be greater than 0 and at most 50 km");
        }

        preference.setDefaultLocationLabel(request.getDefaultLocationLabel().trim());
        preference.setDefaultLat(request.getDefaultLat());
        preference.setDefaultLng(request.getDefaultLng());
        preference.setDefaultRadiusKm(request.getDefaultRadiusKm());
    }

    private Set<CollectionTimeSlot> normalizeSlots(Set<CollectionTimeSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return slots.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private DiscoveryPreferenceResponse defaultResponse() {
        return DiscoveryPreferenceResponse.builder()
                .preferredDiet(PreferredDiet.NOT_SPECIFIED)
                .preferredCollectionTimes(new LinkedHashSet<>())
                .onboardingStatus(DiscoveryOnboardingStatus.NOT_STARTED)
                .shouldShowOnboarding(true)
                .build();
    }

    private DiscoveryPreferenceResponse toResponse(UserDiscoveryPreference preference) {
        DiscoveryOnboardingStatus onboardingStatus = preference.getOnboardingStatus();
        return DiscoveryPreferenceResponse.builder()
                .id(preference.getId())
                .preferredDiet(preference.getPreferredDiet())
                .preferredCollectionTimes(normalizeSlots(preference.getPreferredCollectionTimes()))
                .defaultLocationLabel(preference.getDefaultLocationLabel())
                .defaultLat(preference.getDefaultLat())
                .defaultLng(preference.getDefaultLng())
                .defaultRadiusKm(preference.getDefaultRadiusKm())
                .onboardingStatus(onboardingStatus)
                .shouldShowOnboarding(onboardingStatus == DiscoveryOnboardingStatus.NOT_STARTED)
                .createdAt(preference.getCreatedAt())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }
}
