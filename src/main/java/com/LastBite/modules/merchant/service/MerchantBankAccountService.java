package com.LastBite.modules.merchant.service;

import com.LastBite.common.exception.*;
import com.LastBite.common.security.SensitiveDataCipher;
import com.LastBite.modules.merchant.dto.request.BankAccountRequest;
import com.LastBite.modules.merchant.dto.response.BankAccountResponse;
import com.LastBite.modules.merchant.entity.MerchantBankAccount;
import com.LastBite.modules.merchant.enums.BankAccountVerificationStatus;
import com.LastBite.modules.merchant.repository.*;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
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

    private BankAccountResponse response(MerchantBankAccount account) {
        return BankAccountResponse.builder()
                .id(account.getId())
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
