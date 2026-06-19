package com.LastBite.modules.settlement.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.security.SensitiveDataCipher;
import com.LastBite.modules.audit.service.AdminAuditLogService;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.ledger.entity.LedgerAccount;
import com.LastBite.modules.ledger.entity.LedgerEntry;
import com.LastBite.modules.ledger.enums.LedgerAccountType;
import com.LastBite.modules.ledger.enums.LedgerEntryDirection;
import com.LastBite.modules.ledger.enums.LedgerOwnerType;
import com.LastBite.modules.ledger.repository.LedgerAccountRepository;
import com.LastBite.modules.ledger.repository.LedgerEntryRepository;
import com.LastBite.modules.merchant.entity.MerchantBankAccount;
import com.LastBite.modules.merchant.entity.MerchantBusinessProfile;
import com.LastBite.modules.merchant.enums.BankAccountVerificationStatus;
import com.LastBite.modules.merchant.repository.MerchantBankAccountRepository;
import com.LastBite.modules.merchant.repository.MerchantBusinessProfileRepository;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.payment.entity.PaymentGatewayRequest;
import com.LastBite.modules.payment.enums.GatewayRequestStatus;
import com.LastBite.modules.payment.enums.GatewayRequestType;
import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.payment.gateway.CreatePayoutCommand;
import com.LastBite.modules.payment.gateway.PayoutGatewayPort;
import com.LastBite.modules.payment.gateway.PayoutResult;
import com.LastBite.modules.payment.repository.PaymentGatewayRequestRepository;
import com.LastBite.modules.refund.enums.RefundStatus;
import com.LastBite.modules.settlement.dto.request.MarkPayoutFailedRequest;
import com.LastBite.modules.settlement.dto.request.MarkPayoutPaidRequest;
import com.LastBite.modules.settlement.dto.response.MerchantPayableBalanceResponse;
import com.LastBite.modules.settlement.dto.response.PayoutResponse;
import com.LastBite.modules.settlement.dto.response.SettlementResponse;
import com.LastBite.modules.settlement.entity.MerchantSettlement;
import com.LastBite.modules.settlement.entity.StorePayout;
import com.LastBite.modules.settlement.enums.MerchantSettlementStatus;
import com.LastBite.modules.settlement.enums.PayoutStatus;
import com.LastBite.modules.settlement.repository.MerchantSettlementRepository;
import com.LastBite.modules.settlement.repository.StorePayoutRepository;
import com.LastBite.modules.store.entity.Store;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String VND = "VND";
    private static final List<RefundStatus> OPEN_REFUND_STATUSES = List.of(
            RefundStatus.PENDING_REVIEW,
            RefundStatus.APPROVED,
            RefundStatus.PROCESSING,
            RefundStatus.FAILED);

    private final MerchantSettlementRepository settlementRepository;
    private final StorePayoutRepository payoutRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final MerchantBusinessProfileRepository businessProfileRepository;
    private final MerchantBankAccountRepository bankAccountRepository;
    private final PaymentGatewayRequestRepository gatewayRequestRepository;
    private final PayoutGatewayPort payoutGateway;
    private final SensitiveDataCipher cipher;
    private final UserRepository userRepository;
    private final AdminAuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public List<SettlementResponse> createWeeklyDrafts(UUID adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        List<LedgerEntry> entries = ledgerEntryRepository.findUnsettledSettleableMerchantEntries(
                LedgerAccountType.MERCHANT_PAYABLE,
                Instant.now(clock),
                OPEN_REFUND_STATUSES);
        Map<SettlementKey, List<LedgerEntry>> byStore = entries.stream()
                .filter(entry -> entry.getOrder() != null)
                .collect(Collectors.groupingBy(entry -> {
                    Store store = entry.getOrder().getStore();
                    return new SettlementKey(store.getBusinessProfile().getId(), store.getId());
                }));

        List<SettlementResponse> responses = new ArrayList<>();
        for (List<LedgerEntry> group : byStore.values()) {
            MerchantSettlement settlement = createDraftForGroup(group);
            group.forEach(entry -> entry.setSettlementId(settlement.getId()));
            ledgerEntryRepository.saveAll(group);
            auditLogService.record(admin, "SETTLEMENT_DRAFT_CREATE", "MERCHANT_SETTLEMENT",
                    settlement.getId(), null, "entryCount=" + group.size());
            responses.add(toSettlementResponse(settlement));
        }
        return responses;
    }

    @Transactional
    public SettlementResponse approve(UUID adminId, UUID settlementId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        MerchantSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay settlement"));
        if (settlement.getStatus() != MerchantSettlementStatus.DRAFT) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi settlement DRAFT moi duoc approve");
        }
        settlement.setStatus(MerchantSettlementStatus.APPROVED);
        settlement.setApprovedBy(admin);
        settlement.setApprovedAt(Instant.now(clock));
        if (settlement.getNetAmount().signum() <= 0) {
            settlement.setStatus(MerchantSettlementStatus.PAID);
            settlement.setPaidAt(settlement.getApprovedAt());
            auditLogService.record(admin, "SETTLEMENT_CLOSE_NON_POSITIVE_NET", "MERCHANT_SETTLEMENT",
                    settlement.getId(), null, "netAmount=" + settlement.getNetAmount());
            return toSettlementResponse(settlement);
        }
        auditLogService.record(admin, "SETTLEMENT_APPROVE", "MERCHANT_SETTLEMENT",
                settlement.getId(), null, "netAmount=" + settlement.getNetAmount());
        return toSettlementResponse(settlement);
    }

    @Transactional
    public PayoutResponse startPayout(UUID adminId, UUID settlementId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        MerchantSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay settlement"));
        if (settlement.getStatus() != MerchantSettlementStatus.APPROVED
                && settlement.getStatus() != MerchantSettlementStatus.FAILED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Settlement phai APPROVED truoc khi payout");
        }
        Optional<StorePayout> existing = payoutRepository.findBySettlementId(settlementId);
        if (existing.isPresent()) {
            StorePayout payout = existing.get();
            if (payout.getStatus() != PayoutStatus.FAILED) {
                return toPayoutResponse(payout);
            }
            return retryFailedPayout(admin, settlement, payout);
        }
        MerchantBankAccount bankAccount = bankAccountRepository
                .findFirstByBusinessProfileIdAndVerificationStatusOrderByDefaultAccountDescCreatedAtAsc(
                        settlement.getBusinessProfile().getId(),
                        BankAccountVerificationStatus.APPROVED)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INPUT,
                        "Merchant chua co tai khoan ngan hang da duoc duyet"));

        StorePayout payout = payoutRepository.save(StorePayout.builder()
                .settlement(settlement)
                .store(settlement.getStore())
                .bankAccount(bankAccount)
                .provider(PaymentProvider.PAYOS)
                .idempotencyKey("settlement:" + settlement.getId() + ":payout:v1")
                .amount(settlement.getNetAmount())
                .status(PayoutStatus.PROCESSING)
                .requestedAt(Instant.now(clock))
                .build());
        settlement.setStatus(MerchantSettlementStatus.PAYOUT_PROCESSING);
        callPayoutGateway(settlement, payout);
        auditLogService.record(admin, "PAYOUT_START", "STORE_PAYOUT", payout.getId(),
                null, "settlementId=" + settlementId + ";amount=" + payout.getAmount());
        return toPayoutResponse(payout);
    }

    private PayoutResponse retryFailedPayout(User admin, MerchantSettlement settlement, StorePayout payout) {
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setFailureReason(null);
        payout.setRequestedAt(Instant.now(clock));
        settlement.setStatus(MerchantSettlementStatus.PAYOUT_PROCESSING);
        callPayoutGateway(settlement, payout);
        auditLogService.record(admin, "PAYOUT_RETRY", "STORE_PAYOUT", payout.getId(),
                null, "settlementId=" + settlement.getId() + ";amount=" + payout.getAmount());
        return toPayoutResponse(payout);
    }

    private void callPayoutGateway(MerchantSettlement settlement, StorePayout payout) {
        MerchantBankAccount bankAccount = payout.getBankAccount();
        String accountNumber = cipher.decrypt(bankAccount.getAccountNumberEncrypted());
        String referenceId = "settlement_" + settlement.getId();
        PaymentGatewayRequest requestLog = gatewayRequestRepository
                .findByProviderAndIdempotencyKey(PaymentProvider.PAYOS, payout.getIdempotencyKey())
                .orElseGet(() -> gatewayRequestRepository.save(PaymentGatewayRequest.builder()
                        .provider(PaymentProvider.PAYOS)
                        .requestType(GatewayRequestType.CREATE_PAYOUT)
                        .idempotencyKey(payout.getIdempotencyKey())
                        .status(GatewayRequestStatus.PENDING)
                        .build()));
        requestLog.setStatus(GatewayRequestStatus.PENDING);
        requestLog.setRequestPayload(maskedPayoutPayload(referenceId, payout, bankAccount));
        requestLog.setErrorMessage(null);

        try {
            PayoutResult result = payoutGateway.createPayout(new CreatePayoutCommand(
                    referenceId,
                    payout.getAmount(),
                    "LastBite settlement " + settlement.getId().toString().substring(0, 8),
                    bankAccount.getBankCode(),
                    accountNumber,
                    payout.getIdempotencyKey(),
                    "merchant_settlement"));
            payout.setProviderPayoutId(result.providerPayoutId());
            payout.setProviderTransactionId(result.providerTransactionId());
            payout.setStatus(isPaidState(result.state()) ? PayoutStatus.PAID : PayoutStatus.PROCESSING);
            if (payout.getStatus() == PayoutStatus.PAID) {
                payout.setPaidAt(Instant.now(clock));
                settlement.setStatus(MerchantSettlementStatus.PAID);
                settlement.setPaidAt(payout.getPaidAt());
            }
            requestLog.setStatus(GatewayRequestStatus.SUCCEEDED);
            requestLog.setProviderReference(result.providerPayoutId());
            requestLog.setResponsePayload(result.rawResponse());
        } catch (Exception ex) {
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(ex.getMessage());
            settlement.setStatus(MerchantSettlementStatus.FAILED);
            requestLog.setStatus(GatewayRequestStatus.FAILED);
            requestLog.setErrorMessage(ex.getMessage());
        }
    }

    @Transactional
    public PayoutResponse markPayoutPaid(UUID adminId, UUID payoutId, MarkPayoutPaidRequest request) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        StorePayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay payout"));
        payout.setStatus(PayoutStatus.PAID);
        payout.setProviderPayoutId(trimToNull(request.getProviderPayoutId()));
        payout.setProviderTransactionId(trimToNull(request.getProviderTransactionId()));
        payout.setPaidAt(Instant.now(clock));
        payout.getSettlement().setStatus(MerchantSettlementStatus.PAID);
        payout.getSettlement().setPaidAt(payout.getPaidAt());
        auditLogService.record(admin, "PAYOUT_MARK_PAID", "STORE_PAYOUT", payout.getId(),
                null, "settlementId=" + payout.getSettlement().getId());
        return toPayoutResponse(payout);
    }

    @Transactional
    public PayoutResponse markPayoutFailed(UUID adminId, UUID payoutId, MarkPayoutFailedRequest request) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        StorePayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay payout"));
        payout.setStatus(PayoutStatus.FAILED);
        payout.setFailureReason(request.getFailureReason().trim());
        payout.getSettlement().setStatus(MerchantSettlementStatus.FAILED);
        auditLogService.record(admin, "PAYOUT_MARK_FAILED", "STORE_PAYOUT", payout.getId(),
                payout.getFailureReason(), "settlementId=" + payout.getSettlement().getId());
        return toPayoutResponse(payout);
    }

    @Transactional(readOnly = true)
    public Page<SettlementResponse> listAdmin(MerchantSettlementStatus status, Pageable pageable) {
        Page<MerchantSettlement> settlements = status == null
                ? settlementRepository.findAll(pageable)
                : settlementRepository.findAllByStatus(status, pageable);
        return settlements.map(this::toSettlementResponse);
    }

    @Transactional(readOnly = true)
    public Page<SettlementResponse> listMerchant(UUID ownerId, Pageable pageable) {
        return settlementRepository.findAllByBusinessProfileOwnerId(ownerId, pageable)
                .map(this::toSettlementResponse);
    }

    @Transactional(readOnly = true)
    public Page<PayoutResponse> listMerchantPayouts(UUID ownerId, Pageable pageable) {
        return payoutRepository.findAllBySettlementBusinessProfileOwnerId(ownerId, pageable)
                .map(this::toPayoutResponse);
    }

    @Transactional(readOnly = true)
    public MerchantPayableBalanceResponse payableBalance(UUID ownerId) {
        MerchantBusinessProfile profile = businessProfileRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so merchant"));
        BigDecimal balance = ledgerAccountRepository
                .findByOwnerTypeAndOwnerIdAndAccountTypeAndCurrency(
                        LedgerOwnerType.MERCHANT,
                        profile.getId(),
                        LedgerAccountType.MERCHANT_PAYABLE,
                        VND)
                .map(LedgerAccount::getBalance)
                .orElse(BigDecimal.ZERO);
        return MerchantPayableBalanceResponse.builder()
                .businessProfileId(profile.getId())
                .balance(balance)
                .currency(VND)
                .build();
    }

    private MerchantSettlement createDraftForGroup(List<LedgerEntry> entries) {
        Order firstOrder = entries.getFirst().getOrder();
        Store store = firstOrder.getStore();
        BigDecimal net = entries.stream()
                .map(this::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal merchantCredit = entries.stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundAmount = entries.stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, Order> grossOrders = entries.stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.CREDIT)
                .map(LedgerEntry::getOrder)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Order::getId, order -> order, (left, right) -> left));
        BigDecimal gross = grossOrders.values().stream()
                .map(Order::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commission = gross.subtract(merchantCredit).max(BigDecimal.ZERO);
        LocalDate periodStart = entries.stream()
                .map(LedgerEntry::getOrder)
                .filter(Objects::nonNull)
                .map(Order::getPickupDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now(clock));
        LocalDate periodEnd = entries.stream()
                .map(LedgerEntry::getOrder)
                .filter(Objects::nonNull)
                .map(Order::getPickupDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now(clock));

        return settlementRepository.save(MerchantSettlement.builder()
                .businessProfile(store.getBusinessProfile())
                .store(store)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .grossAmount(gross)
                .commissionAmount(commission)
                .refundAmount(refundAmount)
                .netAmount(net)
                .status(MerchantSettlementStatus.DRAFT)
                .build());
    }

    private BigDecimal signedAmount(LedgerEntry entry) {
        return entry.getDirection() == LedgerEntryDirection.DEBIT
                ? entry.getAmount().negate()
                : entry.getAmount();
    }

    private SettlementResponse toSettlementResponse(MerchantSettlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .businessProfileId(settlement.getBusinessProfile().getId())
                .storeId(settlement.getStore() == null ? null : settlement.getStore().getId())
                .periodStart(settlement.getPeriodStart())
                .periodEnd(settlement.getPeriodEnd())
                .grossAmount(settlement.getGrossAmount())
                .commissionAmount(settlement.getCommissionAmount())
                .refundAmount(settlement.getRefundAmount())
                .netAmount(settlement.getNetAmount())
                .status(settlement.getStatus())
                .approvedByUserId(settlement.getApprovedBy() == null ? null : settlement.getApprovedBy().getId())
                .approvedAt(settlement.getApprovedAt())
                .paidAt(settlement.getPaidAt())
                .createdAt(settlement.getCreatedAt())
                .updatedAt(settlement.getUpdatedAt())
                .build();
    }

    private PayoutResponse toPayoutResponse(StorePayout payout) {
        return PayoutResponse.builder()
                .id(payout.getId())
                .settlementId(payout.getSettlement().getId())
                .storeId(payout.getStore() == null ? null : payout.getStore().getId())
                .bankAccountId(payout.getBankAccount() == null ? null : payout.getBankAccount().getId())
                .provider(payout.getProvider())
                .idempotencyKey(payout.getIdempotencyKey())
                .amount(payout.getAmount())
                .status(payout.getStatus())
                .providerPayoutId(payout.getProviderPayoutId())
                .providerTransactionId(payout.getProviderTransactionId())
                .failureReason(payout.getFailureReason())
                .requestedAt(payout.getRequestedAt())
                .paidAt(payout.getPaidAt())
                .createdAt(payout.getCreatedAt())
                .updatedAt(payout.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isPaidState(String state) {
        return "SUCCEEDED".equalsIgnoreCase(state)
                || "SUCCESS".equalsIgnoreCase(state)
                || "PAID".equalsIgnoreCase(state);
    }

    private String maskedPayoutPayload(String referenceId, StorePayout payout, MerchantBankAccount bankAccount) {
        return toJson(Map.of(
                "referenceId", referenceId,
                "amount", payout.getAmount(),
                "toBin", bankAccount.getBankCode(),
                "toAccountNumber", "****" + bankAccount.getAccountNumberLast4()
        ));
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record SettlementKey(UUID businessProfileId, UUID storeId) {
    }
}
