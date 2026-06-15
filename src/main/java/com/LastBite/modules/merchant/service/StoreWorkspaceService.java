package com.LastBite.modules.merchant.service;

import com.LastBite.common.exception.*;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import com.LastBite.modules.store.service.impl.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreWorkspaceService {
    private final MerchantStoreMemberRepository memberRepository;
    private final StoreService storeService;

    @Transactional(readOnly = true)
    public StoreDetailResponse currentStore(UUID userId) {
        var membership = memberRepository.findByUserId(userId)
                .filter(item -> item.getStatus() == StoreMemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
        return storeService.toDetailResponse(membership.getStore());
    }
}
