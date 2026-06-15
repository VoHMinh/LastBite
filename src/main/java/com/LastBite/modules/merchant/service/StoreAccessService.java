package com.LastBite.modules.merchant.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreAccessService {
    private final StoreRepository storeRepository;
    private final MerchantStoreMemberRepository memberRepository;

    public Store require(UUID userId, UUID storeId, Set<UserRole> memberRoles) {
        Store store = storeRepository.findDetailById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        if (store.getBusinessProfile().getOwner().getId().equals(userId)) return store;
        var member = memberRepository.findByUserIdAndStoreIdAndStatus(
                        userId, storeId, StoreMemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
        if (!memberRoles.contains(member.getRole().getCode())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return store;
    }
}
