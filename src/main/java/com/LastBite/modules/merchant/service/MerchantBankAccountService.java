package com.LastBite.modules.merchant.service;

import com.LastBite.common.exception.*;
import com.LastBite.common.response.PageResponse;
import com.LastBite.common.security.SensitiveDataCipher;
import com.LastBite.modules.audit.service.AdminAuditLogService;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.merchant.dto.request.BankAccountRequest;
import com.LastBite.modules.merchant.dto.request.RejectBankAccountRequest;
import com.LastBite.modules.merchant.dto.response.BankAccountResponse;
import com.LastBite.modules.merchant.entity.MerchantBankAccount;
import com.LastBite.modules.merchant.enums.BankAccountVerificationStatus;
import com.LastBite.modules.merchant.repository.*;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MerchantBankAccountService {
    private final MerchantBankAccountRepository bankRepository;
    private final MerchantBusinessProfileRepository profileRepository;
    private final StoreRepository storeRepository;
    private final SensitiveDataCipher cipher;
    private final UserRepository userRepository;
    private final AdminAuditLogService auditLogService;

    @Transactional
    public BankAccountResponse create(UUID ownerId, BankAccountRequest request) {
        var profile = profileRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Chưa tạo Business Profile"));
        var store = request.getStoreId() == null ? null
                : storeRepository.findByIdAndBusinessProfileOwnerId(request.getStoreId(), ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        String number = request.getAccountNumber().replaceAll("\\s+", "");
        MerchantBankAccount account = MerchantBankAccount.builder()
                .businessProfile(profile)
                .store(store)
                .bankCode(request.getBankCode().trim())
                .bankName(request.getBankName().trim())
                .accountHolderName(request.getAccountHolderName().trim())
                .accountNumberEncrypted(cipher.encrypt(number))
                .accountNumberLast4(number.substring(number.length() - 4))
                .defaultAccount(request.isDefaultAccount())
                .verificationStatus(BankAccountVerificationStatus.PENDING_REVIEW)
                .build();
        return response(bankRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list(UUID ownerId) {
        return bankRepository.findAllByBusinessProfileOwnerIdOrderByCreatedAtAsc(ownerId)
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BankAccountResponse> listAdmin(BankAccountVerificationStatus status,
                                                       UUID businessProfileId,
                                                       UUID storeId,
                                                       Pageable pageable) {
        var page = bankRepository.searchAdmin(status, businessProfileId, storeId, pageable)
                .map(this::response);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BankAccountResponse getAdmin(UUID bankAccountId) {
        return bankRepository.findById(bankAccountId)
                .map(this::response)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay tai khoan ngan hang"));
    }

    @Transactional
    public BankAccountResponse approve(UUID adminId, UUID bankAccountId) {
        var admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        MerchantBankAccount account = bankRepository.findById(bankAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay tai khoan ngan hang"));
        if (account.getVerificationStatus() != BankAccountVerificationStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi tai khoan PENDING_REVIEW moi duoc duyet");
        }
        account.setVerificationStatus(BankAccountVerificationStatus.APPROVED);
        account.setRejectionReason(null);
        auditLogService.record(admin, "BANK_ACCOUNT_APPROVE", "MERCHANT_BANK_ACCOUNT", account.getId(),
                null, "businessProfileId=" + account.getBusinessProfile().getId());
        return response(account);
    }

    @Transactional
    public BankAccountResponse reject(UUID adminId, UUID bankAccountId, RejectBankAccountRequest request) {
        var admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        MerchantBankAccount account = bankRepository.findById(bankAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay tai khoan ngan hang"));
        if (account.getVerificationStatus() != BankAccountVerificationStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi tai khoan PENDING_REVIEW moi duoc reject");
        }
        account.setVerificationStatus(BankAccountVerificationStatus.REJECTED);
        account.setRejectionReason(request.getRejectionReason().trim());
        auditLogService.record(admin, "BANK_ACCOUNT_REJECT", "MERCHANT_BANK_ACCOUNT", account.getId(),
                account.getRejectionReason(), "businessProfileId=" + account.getBusinessProfile().getId());
        return response(account);
    }

    private BankAccountResponse response(MerchantBankAccount account) {
        return BankAccountResponse.builder()
                .id(account.getId())
                .businessProfileId(account.getBusinessProfile().getId())
                .storeId(account.getStore() == null ? null : account.getStore().getId())
                .bankCode(account.getBankCode())
                .bankName(account.getBankName())
                .accountHolderName(account.getAccountHolderName())
                .maskedAccountNumber("****" + account.getAccountNumberLast4())
                .defaultAccount(account.isDefaultAccount())
                .verificationStatus(account.getVerificationStatus())
                .rejectionReason(account.getRejectionReason())
                .build();
    }
}
