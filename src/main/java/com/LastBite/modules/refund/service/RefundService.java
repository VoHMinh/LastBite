package com.LastBite.modules.refund.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.audit.service.AdminAuditLogService;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.ledger.service.LedgerService;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.repository.PaymentRepository;
import com.LastBite.modules.refund.dto.request.CreateRefundRequest;
import com.LastBite.modules.refund.dto.request.ReviewRefundRequest;
import com.LastBite.modules.refund.dto.response.RefundResponse;
import com.LastBite.modules.refund.entity.RefundRequest;
import com.LastBite.modules.refund.entity.RefundTransaction;
import com.LastBite.modules.refund.enums.*;
import com.LastBite.modules.refund.repository.RefundRequestRepository;
import com.LastBite.modules.refund.repository.RefundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
        RefundRequest refund = refundRepository.save(RefundRequest.builder()
                .order(order)
                .payment(payment)
                .requestedBy(user)
                .reason(request.getReason())
                .status(RefundStatus.PENDING_REVIEW)
                .requestedAmount(order.getFinalAmount())
                .description(trimToNull(request.getDescription()))
                .build());
        order.setRefundStatus(OrderRefundStatus.REQUESTED);
        return toResponse(refund);
    }

    @Transactional
    public RefundRequest createAutoRefund(Order order, Payment payment, RefundReason reason, String description) {
        if (refundRepository.existsByOrderId(order.getId())) {
            return refundRepository.findByOrderId(order.getId()).getFirst();
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
        transactionRepository.save(RefundTransaction.builder()
                .refundRequest(refund)
                .amount(refund.getApprovedAmount())
                .status(RefundTransactionStatus.PENDING)
                .method(RefundTransactionMethod.MANUAL_BANK_TRANSFER)
                .build());
        order.setRefundStatus(OrderRefundStatus.APPROVED);
        ledgerService.recordRefundApproved(refund);
        return refund;
    }

    @Transactional
    public RefundResponse review(UUID adminId, UUID refundId, ReviewRefundRequest request) {
        RefundRequest refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay refund request"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (refund.getStatus() != RefundStatus.PENDING_REVIEW && refund.getStatus() != RefundStatus.APPROVED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Yeu cau hoan tien khong con o trang thai co the review");
        }
        if (Boolean.TRUE.equals(request.getApprove())) {
            refund.setStatus(RefundStatus.APPROVED);
            refund.setApprovedAmount(request.getApprovedAmount() == null ? refund.getRequestedAmount() : request.getApprovedAmount());
            refund.getOrder().setRefundStatus(OrderRefundStatus.APPROVED);
            transactionRepository.save(RefundTransaction.builder()
                    .refundRequest(refund)
                    .amount(refund.getApprovedAmount())
                    .status(RefundTransactionStatus.PENDING)
                    .method(RefundTransactionMethod.MANUAL_BANK_TRANSFER)
                    .build());
            ledgerService.recordRefundApproved(refund);
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
        return RefundResponse.builder()
                .id(refund.getId())
                .orderId(refund.getOrder().getId())
                .paymentId(refund.getPayment() == null ? null : refund.getPayment().getId())
                .requestedByUserId(refund.getRequestedBy() == null ? null : refund.getRequestedBy().getId())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .requestedAmount(refund.getRequestedAmount())
                .approvedAmount(refund.getApprovedAmount())
                .description(refund.getDescription())
                .decisionNote(refund.getDecisionNote())
                .autoCreated(refund.isAutoCreated())
                .createdAt(refund.getCreatedAt())
                .updatedAt(refund.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
