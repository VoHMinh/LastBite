package com.LastBite.modules.user.dto.response;

import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.user.enums.CollectionTimeSlot;
import com.LastBite.modules.user.enums.DiscoveryOnboardingStatus;
import com.LastBite.modules.user.enums.PreferredDiet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryPreferenceResponse implements Serializable {
    private UUID id;
    private PreferredDiet preferredDiet;
    private BagType preferredBagType;
    private Set<CollectionTimeSlot> preferredCollectionTimes;
    private String defaultLocationLabel;
    private Double defaultLat;
    private Double defaultLng;
    private Double defaultRadiusKm;
    private DiscoveryOnboardingStatus onboardingStatus;
    private boolean shouldShowOnboarding;
    private Instant createdAt;
    private Instant updatedAt;
}
