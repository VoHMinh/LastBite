package com.LastBite.modules.user.service;

import com.LastBite.modules.user.dto.request.UpdateDiscoveryPreferenceRequest;
import com.LastBite.modules.user.dto.response.DiscoveryPreferenceResponse;

import java.util.UUID;

public interface DiscoveryPreferenceServicePort {

    DiscoveryPreferenceResponse get(UUID userId);

    DiscoveryPreferenceResponse update(UUID userId, UpdateDiscoveryPreferenceRequest request);

    DiscoveryPreferenceResponse skipOnboarding(UUID userId);
}
