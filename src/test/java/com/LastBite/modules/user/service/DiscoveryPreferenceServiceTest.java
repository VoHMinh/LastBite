package com.LastBite.modules.user.service;

import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.user.dto.request.UpdateDiscoveryPreferenceRequest;
import com.LastBite.modules.user.entity.UserDiscoveryPreference;
import com.LastBite.modules.user.enums.CollectionTimeSlot;
import com.LastBite.modules.user.enums.DiscoveryOnboardingStatus;
import com.LastBite.modules.user.enums.PreferredDiet;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import com.LastBite.modules.user.service.impl.DiscoveryPreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryPreferenceServiceTest {

    private final UserDiscoveryPreferenceRepository preferenceRepository =
            mock(UserDiscoveryPreferenceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DiscoveryPreferenceService service =
            new DiscoveryPreferenceService(preferenceRepository, userRepository);

    @Test
    void getReturnsDefaultWhenPreferenceDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        var response = service.get(userId);

        assertEquals(PreferredDiet.MEAT, response.getPreferredDiet());
        assertEquals(BagType.STANDARD, response.getPreferredBagType());
        assertTrue(response.getPreferredCollectionTimes().isEmpty());
        assertEquals(DiscoveryOnboardingStatus.NOT_STARTED, response.getOnboardingStatus());
        assertTrue(response.isShouldShowOnboarding());
    }

    @Test
    void updateCreatesCompletedPreferenceWithLocationAndSlots() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(preferenceRepository.save(any(UserDiscoveryPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateDiscoveryPreferenceRequest request = new UpdateDiscoveryPreferenceRequest();
        request.setPreferredDiet(PreferredDiet.VEGETARIAN);
        request.setPreferredCollectionTimes(Set.of(CollectionTimeSlot.EVENING, CollectionTimeSlot.LATE_NIGHT));
        request.setDefaultLocationLabel("Syracuse");
        request.setDefaultLat(43.0481);
        request.setDefaultLng(-76.1474);
        request.setDefaultRadiusKm(22.5);

        var response = service.update(userId, request);

        assertEquals(PreferredDiet.VEGETARIAN, response.getPreferredDiet());
        assertEquals(BagType.STANDARD, response.getPreferredBagType());
        assertEquals(Set.of(CollectionTimeSlot.EVENING, CollectionTimeSlot.LATE_NIGHT),
                response.getPreferredCollectionTimes());
        assertEquals("Syracuse", response.getDefaultLocationLabel());
        assertEquals(22.5, response.getDefaultRadiusKm());
        assertEquals(DiscoveryOnboardingStatus.COMPLETED, response.getOnboardingStatus());
        assertFalse(response.isShouldShowOnboarding());
    }

    @Test
    void skipOnboardingCreatesSkippedPreference() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(preferenceRepository.save(any(UserDiscoveryPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.skipOnboarding(userId);

        assertEquals(DiscoveryOnboardingStatus.SKIPPED, response.getOnboardingStatus());
        assertFalse(response.isShouldShowOnboarding());
    }

    private User user(UUID userId) {
        User user = User.builder()
                .email("user@test.local")
                .fullName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
