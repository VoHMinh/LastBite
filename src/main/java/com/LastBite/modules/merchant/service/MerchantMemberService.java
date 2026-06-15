package com.LastBite.modules.merchant.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.*;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.auth.service.impl.RefreshTokenService;
import com.LastBite.modules.auth.service.impl.RoleAssignmentService;
import com.LastBite.modules.merchant.dto.request.CreateStoreMemberRequest;
import com.LastBite.modules.merchant.dto.response.StoreMemberResponse;
import com.LastBite.modules.merchant.entity.MerchantStoreMember;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantMemberService {
    private static final String TEMP_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MerchantStoreMemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final RoleAssignmentService roleAssignmentService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public StoreMemberResponse create(UUID actorId, UUID storeId, CreateStoreMemberRequest request) {
        Store store = getStore(storeId);
        UserRole actorRole = authorizeMemberManagement(actorId, store);
        if (request.getRole() != UserRole.MANAGER && request.getRole() != UserRole.STAFF) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chỉ được tạo MANAGER hoặc STAFF");
        }
        if (actorRole == UserRole.MANAGER && request.getRole() != UserRole.STAFF) {
            throw new ApiException(ErrorCode.FORBIDDEN, "MANAGER chỉ được tạo STAFF");
        }
        String username = request.getUsername().trim().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(ErrorCode.USERNAME_EXISTS);
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()
                && userRepository.existsByPhone(request.getPhone().trim())) {
            throw new ApiException(ErrorCode.PHONE_EXISTS);
        }
        String temporaryPassword = generateTemporaryPassword();
        User account = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .fullName(request.getFullName().trim())
                .phone(trimToNull(request.getPhone()))
                .accountType(AccountType.STORE_MEMBER)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .phoneVerified(false)
                .mustChangePassword(true)
                .build();
        account = userRepository.save(account);

        MerchantStoreMember member = MerchantStoreMember.builder()
                .user(account)
                .store(store)
                .role(roleAssignmentService.get(request.getRole(), RoleScope.STORE))
                .status(StoreMemberStatus.ACTIVE)
                .createdBy(userRepository.getReferenceById(actorId))
                .joinedAt(Instant.now())
                .build();
        member = memberRepository.save(member);
        return toResponse(member, temporaryPassword);
    }

    @Transactional(readOnly = true)
    public List<StoreMemberResponse> list(UUID actorId, UUID storeId) {
        Store store = getStore(storeId);
        authorizeMemberManagement(actorId, store);
        return memberRepository.findAllByStoreIdOrderByCreatedAtAsc(storeId).stream()
                .map(member -> toResponse(member, null))
                .toList();
    }

    @Transactional
    public StoreMemberResponse suspend(UUID actorId, UUID storeId, UUID memberId) {
        Store store = getStore(storeId);
        UserRole actorRole = authorizeMemberManagement(actorId, store);
        MerchantStoreMember member = getMember(storeId, memberId);
        if (actorRole == UserRole.MANAGER && member.getRole().getCode() != UserRole.STAFF) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        member.setStatus(StoreMemberStatus.SUSPENDED);
        member.getUser().setStatus(UserStatus.INACTIVE);
        userRepository.save(member.getUser());
        refreshTokenService.revokeAllByUserId(member.getUser().getId());
        return toResponse(memberRepository.save(member), null);
    }

    private UserRole authorizeMemberManagement(UUID actorId, Store store) {
        if (store.getBusinessProfile().getOwner().getId().equals(actorId)) {
            return UserRole.MERCHANT_OWNER;
        }
        MerchantStoreMember actor = memberRepository
                .findByUserIdAndStoreIdAndStatus(actorId, store.getId(), StoreMemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
        if (actor.getRole().getCode() != UserRole.MANAGER) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return UserRole.MANAGER;
    }

    private Store getStore(UUID storeId) {
        return storeRepository.findDetailById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
    }

    private MerchantStoreMember getMember(UUID storeId, UUID memberId) {
        MerchantStoreMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (!member.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return member;
    }

    private StoreMemberResponse toResponse(MerchantStoreMember member, String temporaryPassword) {
        return StoreMemberResponse.builder()
                .id(member.getId())
                .userId(member.getUser().getId())
                .storeId(member.getStore().getId())
                .username(member.getUser().getUsername())
                .fullName(member.getUser().getFullName())
                .phone(member.getUser().getPhone())
                .role(member.getRole().getCode())
                .status(member.getStatus())
                .mustChangePassword(member.getUser().isMustChangePassword())
                .temporaryPassword(temporaryPassword)
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private String generateTemporaryPassword() {
        StringBuilder value = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            value.append(TEMP_CHARS.charAt(RANDOM.nextInt(TEMP_CHARS.length())));
        }
        return value.toString();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
