package com.LastBite.modules.refund.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.common.security.SensitiveDataCipher;
import com.LastBite.modules.audit.service.AdminAuditLogService;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.ledger.service.LedgerService;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.payment.enums.PaymentStatus;
import com.LastBite.modules.payment.repository.PaymentRepository;
import com.LastBite.modules.promotion.service.VoucherApplicationService;
import com.LastBite.modules.refund.dto.request.CreateRefundRequest;
import com.LastBite.modules.refund.dto.request.RefundDestinationRequest;
import com.LastBite.modules.refund.dto.request.ReviewRefundRequest;
import com.LastBite.modules.refund.dto.response.RefundResponse;
import com.LastBite.modules.refund.dto.response.RefundTransactionResponse;
import com.LastBite.modules.refund.entity.RefundRequest;
import com.LastBite.modules.refund.entity.RefundTransaction;
import com.LastBite.modules.refund.enums.*;
import com.LastBite.modules.refund.repository.RefundRequestRepository;
import com.LastBite.modules.refund.repository.RefundTransactionRepository;
import com.LastBite.modules.store.service.StoreReliabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {

    private static final Duration DISPUTE_WINDOW = Duration.ofDays(30);

    private final RefundRequestRepository refundRepository;
    private final RefundTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final AdminAuditLogService auditLogService;
    private final NotificationServicePort notificationService;
    private final StoreReliabilityService reliabilityService;
    private final VoucherApplicationService voucherApplicationService;
    private final SensitiveDataCipher cipher;
    private final Clock clock;

    @Transactional
    public RefundResponse requestCustomerRefund(UUID userId, UUID orderId, CreateRefundRequest request) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        if (refundRepository.existsByOrderId(orderId)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Don hang da co yeu cau hoan tien");
        }
        validateCustomerDispute(order);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        RefundRequest refund = RefundRequest.builder()
                .order(order)
                .payment(payment)
                .requestedBy(user)
                .reason(request.getReason())
                .status(RefundStatus.PENDING_REVIEW)
                .requestedAmount(order.getFinalAmount())
                .description(trimToNull(request.getDescription()))
                .build();
        applyDestination(refund, request.getRefundDestination());
        refund = refundRepository.save(refund);
        order.setRefundStatus(OrderRefundStatus.REQUESTED);
        return toResponse(refund);
    }

    @Transactional
    public RefundRequest createAutoRefund(Order order, Payment payment, RefundReason reason, String description) {
        if (refundRepository.existsByOrderId(order.getId())) {
            return refundRepository.findFirstByOrderId(order.getId()).orElseThrow();
        }
        RefundRequest refund = refundRepository.save(RefundRequest.builder()
                .order(order)
                .payment(payment)
                .reason(reason)
                .status(RefundStatus.APPROVED)
                .requestedAmount(order.getFinalAmount())
                .approvedAmount(order.getFinalAmount())
                .description(description)
                .decisionNote("Auto-approved by system")
                .autoCreated(true)
                .reviewedAt(Instant.now(clock))
                .build());
        order.setRefundStatus(OrderRefundStatus.APPROVED);
        ledgerService.recordRefundApproved(refund);
        ensurePayoutTransactionIfPossible(refund);
        reliabilityService.recordStoreFaultRefund(order, reason);
        return refund;
    }

    @Transactional
    public RefundResponse review(UUID adminId, UUID refundId, ReviewRefundRequest request) {
        RefundRequest refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund request"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        RefundStatus previousStatus = refund.getStatus();
        if (refund.getStatus() != RefundStatus.PENDING_REVIEW && refund.getStatus() != RefundStatus.APPROVED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Yeu cau hoan tien khong con o trang thai co the review");
        }
        if (Boolean.TRUE.equals(request.getApprove())) {
            BigDecimal amount = request.getApprovedAmount() == null ? refund.getRequestedAmount() : request.getApprovedAmount();
            validateApprovedAmount(amount, refund.getRequestedAmount());
            refund.setStatus(RefundStatus.APPROVED);
            refund.setApprovedAmount(amount);
            refund.getOrder().setRefundStatus(OrderRefundStatus.APPROVED);
            ledgerService.recordRefundApproved(refund);
            ensurePayoutTransactionIfPossible(refund);
            if (previousStatus != RefundStatus.APPROVED) {
                reliabilityService.recordStoreFaultRefund(refund.getOrder(), refund.getReason());
            }
        } else {
            refund.setStatus(RefundStatus.REJECTED);
            refund.getOrder().setRefundStatus(OrderRefundStatus.REJECTED);
        }
        refund.setDecisionNote(trimToNull(request.getDecisionNote()));
        refund.setReviewedBy(admin);
        refund.setReviewedAt(Instant.now(clock));
        auditLogService.record(admin, "REFUND_REVIEW", "REFUND_REQUEST", refund.getId(),
                refund.getDecisionNote(), "status=" + refund.getStatus());
        return toResponse(refund);
    }

    @Transactional
    public RefundResponse updateDestination(UUID userId, UUID refundId, RefundDestinationRequest request) {
        RefundRequest refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund request"));
        if (!refund.getOrder().getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (refund.getStatus() == RefundStatus.REFUNDED || refund.getStatus() == RefundStatus.REJECTED
                || refund.getStatus() == RefundStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Khong the cap nhat tai khoan nhan tien cho refund nay");
        }
        applyDestination(refund, request);
        ensurePayoutTransactionIfPossible(refund);
        return toResponse(refund);
    }

    @Transactional(readOnly = true)
    public RefundResponse getCustomerRefundByOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        RefundRequest refund = refundRepository.findFirstByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Don hang chua co refund request"));
        return toResponse(refund);
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> listAdmin(RefundStatus status, RefundReason reason, Pageable pageable) {
        var page = refundRepository.searchAdmin(status, reason, pageable).map(this::toResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public RefundResponse retry(UUID adminId, UUID refundId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        RefundRequest refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund request"));
        if (refund.getStatus() == RefundStatus.REFUNDED || refund.getStatus() == RefundStatus.REJECTED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Refund nay khong the retry");
        }
        ensureDestination(refund);
        ensurePayoutTransaction(refund);
        auditLogService.record(admin, "REFUND_RETRY", "REFUND_REQUEST", refund.getId(),
                "Retry refund payout", "status=" + refund.getStatus());
        return toResponse(refund);
    }

    @Transactional
    public RefundResponse markTransactionSucceeded(UUID adminId, UUID transactionId, String providerReference, String note) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        RefundTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund transaction"));
        completeTransaction(transaction, trimToNull(providerReference), trimToNull(note), "Manual admin success");
        auditLogService.record(admin, "REFUND_TRANSACTION_MARK_SUCCEEDED", "REFUND_TRANSACTION", transaction.getId(),
                trimToNull(note), "refundId=" + transaction.getRefundRequest().getId());
        return toResponse(transaction.getRefundRequest());
    }

    @Transactional
    public RefundResponse markTransactionFailed(UUID adminId, UUID transactionId, String failureReason) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        RefundTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund transaction"));
        failTransaction(transaction, failureReason, null);
        auditLogService.record(admin, "REFUND_TRANSACTION_MARK_FAILED", "REFUND_TRANSACTION", transaction.getId(),
                failureReason, "refundId=" + transaction.getRefundRequest().getId());
        return toResponse(transaction.getRefundRequest());
    }

    @Transactional
    public void completeGatewayTransaction(UUID transactionId, String providerReference, String rawPayload) {
        RefundTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund transaction"));
        completeTransaction(transaction, trimToNull(providerReference), rawPayload, "PayOS payout succeeded");
    }

    @Transactional
    public void failGatewayTransaction(UUID transactionId, String failureReason, String rawPayload) {
        RefundTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund transaction"));
        failTransaction(transaction, failureReason, rawPayload);
    }

    @Transactional
    public void markProcessing(UUID transactionId) {
        RefundTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund transaction"));
        RefundRequest refund = transaction.getRefundRequest();
        transaction.setAttemptCount(transaction.getAttemptCount() + 1);
        transaction.setFailureReason(null);
        refund.setStatus(RefundStatus.PROCESSING);
    }

    private void validateCustomerDispute(Order order) {
        if (order.getStatus() != OrderStatus.PICKED_UP && order.getStatus() != OrderStatus.EXPIRED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi co the khieu nai sau khi don da ket thuc");
        }
        Instant pickupEnd = order.getPickedUpAt() != null ? order.getPickedUpAt() : order.getExpiredAt();
        if (pickupEnd == null || pickupEnd.plus(DISPUTE_WINDOW).isBefore(Instant.now(clock))) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Da qua han 30 ngay de gui yeu cau hoan tien");
        }
    }

    public RefundResponse toResponse(RefundRequest refund) {
        List<RefundTransactionResponse> transactions = refund.getId() == null
                ? List.of()
                : transactionRepository.findAllByRefundRequestIdOrderByCreatedAtAsc(refund.getId()).stream()
                .map(this::toTransactionResponse)
                .toList();
        return RefundResponse.builder()
                .id(refund.getId())
                .orderId(refund.getOrder().getId())
                .paymentId(refund.getPayment() == null ? null : refund.getPayment().getId())
                .requestedByUserId(refund.getRequestedBy() == null ? null : refund.getRequestedBy().getId())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .requestedAmount(refund.getRequestedAmount())
                .approvedAmount(refund.getApprovedAmount())
                .reviewedByUserId(refund.getReviewedBy() == null ? null : refund.getReviewedBy().getId())
                .reviewedAt(refund.getReviewedAt())
                .description(refund.getDescription())
                .decisionNote(refund.getDecisionNote())
                .autoCreated(refund.isAutoCreated())
                .refundBankCode(refund.getRefundBankCode())
                .refundBankName(refund.getRefundBankName())
                .refundAccountHolderName(refund.getRefundAccountHolderName())
                .maskedRefundAccountNumber(maskedAccount(refund))
                .destinationRequired(destinationRequired(refund))
                .transactions(transactions)
                .createdAt(refund.getCreatedAt())
                .updatedAt(refund.getUpdatedAt())
                .build();
    }

    private RefundTransactionResponse toTransactionResponse(RefundTransaction transaction) {
        return RefundTransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .method(transaction.getMethod())
                .provider(transaction.getProvider())
                .providerReference(transaction.getProviderReference())
                .failureReason(transaction.getFailureReason())
                .attemptCount(transaction.getAttemptCount())
                .processedAt(transaction.getProcessedAt())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    private void applyDestination(RefundRequest refund, RefundDestinationRequest request) {
        if (request == null) {
            return;
        }
        String accountNumber = request.getAccountNumber().replaceAll("\\s+", "");
        if (accountNumber.length() < 4) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "So tai khoan nhan hoan tien khong hop le");
        }
        refund.setRefundBankCode(request.getBankCode().trim());
        refund.setRefundBankName(request.getBankName().trim());
        refund.setRefundAccountHolderName(request.getAccountHolderName().trim());
        refund.setRefundAccountNumberEncrypted(cipher.encrypt(accountNumber));
        refund.setRefundAccountNumberLast4(accountNumber.substring(accountNumber.length() - 4));
    }

    private RefundTransaction ensurePayoutTransactionIfPossible(RefundRequest refund) {
        if (!hasDestination(refund) || refund.getStatus() != RefundStatus.APPROVED) {
            return null;
        }
        return ensurePayoutTransaction(refund);
    }

    private RefundTransaction ensurePayoutTransaction(RefundRequest refund) {
        ensureDestination(refund);
        if (refund.getApprovedAmount() == null || refund.getApprovedAmount().signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Refund chua co so tien duoc duyet");
        }
        List<RefundTransaction> existing = transactionRepository.findAllByRefundRequestIdOrderByCreatedAtAsc(refund.getId());
        if (existing.stream().anyMatch(transaction -> transaction.getStatus() == RefundTransactionStatus.SUCCEEDED)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Refund da duoc chi tien");
        }
        RefundTransaction pending = existing.stream()
                .filter(transaction -> transaction.getStatus() == RefundTransactionStatus.PENDING)
                .findFirst()
                .orElse(null);
        if (pending != null) {
            refund.setStatus(RefundStatus.PROCESSING);
            return pending;
        }
        RefundTransaction transaction = transactionRepository.save(RefundTransaction.builder()
                .refundRequest(refund)
                .amount(refund.getApprovedAmount())
                .status(RefundTransactionStatus.PENDING)
                .method(RefundTransactionMethod.PAYOS_PAYOUT)
                .provider(PaymentProvider.PAYOS)
                .idempotencyKey("refund:" + refund.getId() + ":attempt:" + (existing.size() + 1))
                .build());
        refund.setStatus(RefundStatus.PROCESSING);
        return transaction;
    }

    private void ensureDestination(RefundRequest refund) {
        if (!hasDestination(refund)) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "Can bo sung tai khoan ngan hang nhan hoan tien");
        }
    }

    private void completeTransaction(RefundTransaction transaction, String providerReference,
                                     String rawPayload, String defaultRawPayload) {
        if (transaction.getStatus() == RefundTransactionStatus.SUCCEEDED) {
            return;
        }
        RefundRequest refund = transaction.getRefundRequest();
        transaction.setStatus(RefundTransactionStatus.SUCCEEDED);
        transaction.setProviderReference(providerReference);
        transaction.setRawPayload(rawPayload == null ? defaultRawPayload : rawPayload);
        transaction.setFailureReason(null);
        transaction.setProcessedAt(Instant.now(clock));

        refund.setStatus(RefundStatus.REFUNDED);
        Order order = refund.getOrder();
        boolean fullRefund = isFullRefund(refund, transaction.getAmount());
        order.setRefundStatus(fullRefund ? OrderRefundStatus.REFUNDED : OrderRefundStatus.PARTIALLY_REFUNDED);
        Payment payment = refund.getPayment();
        if (payment != null) {
            payment.setStatus(fullRefund ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        }
        ledgerService.recordRefundPaid(refund, transaction.getAmount());
        if (fullRefund) {
            voucherApplicationService.reissueAfterFullRefundIfEligible(order, refund.getReason());
        }
        notificationService.notifyOrderRefunded(order);
    }

    private void failTransaction(RefundTransaction transaction, String failureReason, String rawPayload) {
        transaction.setStatus(RefundTransactionStatus.FAILED);
        transaction.setFailureReason(trimToNull(failureReason));
        transaction.setRawPayload(rawPayload == null ? transaction.getRawPayload() : rawPayload);
        transaction.setProcessedAt(Instant.now(clock));
        RefundRequest refund = transaction.getRefundRequest();
        refund.setStatus(RefundStatus.FAILED);
    }

    private boolean isFullRefund(RefundRequest refund, BigDecimal amount) {
        BigDecimal fullAmount = refund.getPayment() == null ? refund.getOrder().getFinalAmount() : refund.getPayment().getAmount();
        return amount.compareTo(fullAmount) >= 0;
    }

    private boolean destinationRequired(RefundRequest refund) {
        return !hasDestination(refund)
                && (refund.getStatus() == RefundStatus.APPROVED
                || refund.getStatus() == RefundStatus.PROCESSING
                || refund.getStatus() == RefundStatus.FAILED);
    }

    private boolean hasDestination(RefundRequest refund) {
        return refund.getRefundBankCode() != null
                && refund.getRefundAccountNumberEncrypted() != null
                && refund.getRefundAccountNumberLast4() != null;
    }

    private String maskedAccount(RefundRequest refund) {
        return refund.getRefundAccountNumberLast4() == null ? null : "****" + refund.getRefundAccountNumberLast4();
    }

    private void validateApprovedAmount(BigDecimal amount, BigDecimal requestedAmount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(requestedAmount) > 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "So tien hoan khong hop le");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
