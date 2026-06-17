package com.LastBite.modules.ledger.service;

import com.LastBite.modules.ledger.entity.LedgerAccount;
import com.LastBite.modules.ledger.entity.LedgerEntry;
import com.LastBite.modules.ledger.enums.*;
import com.LastBite.modules.ledger.repository.LedgerAccountRepository;
import com.LastBite.modules.ledger.repository.LedgerEntryRepository;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.refund.entity.RefundRequest;
import com.LastBite.modules.settlement.entity.PlatformCommission;
import com.LastBite.modules.settlement.enums.CommissionStatus;
import com.LastBite.modules.settlement.repository.PlatformCommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private static final String VND = "VND";
    private static final int MERCHANT_HOLD_DAYS = 3;

    private final LedgerAccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final PlatformCommissionRepository commissionRepository;

    @Transactional
    public void recordPaymentCaptured(Order order, Payment payment) {
        if (entryRepository.existsByOrderIdAndEntryType(order.getId(), LedgerEntryType.PAYMENT_CAPTURED)) {
            return;
        }
        LedgerAccount cash = account(LedgerOwnerType.PLATFORM, null, LedgerAccountType.PLATFORM_CASH);
        LedgerAccount escrow = account(LedgerOwnerType.PLATFORM, null, LedgerAccountType.ESCROW);
        credit(cash, order, payment, null, LedgerEntryType.PAYMENT_CAPTURED, payment.getAmount(), null,
                "PayOS payment captured for " + order.getOrderNumber());
        credit(escrow, order, payment, null, LedgerEntryType.ESCROW_HELD, payment.getAmount(), null,
                "Escrow held for " + order.getOrderNumber());
    }

    @Transactional
    public void recordOrderCompleted(Order order, Payment payment, Instant completedAt) {
        if (entryRepository.existsByOrderIdAndEntryType(order.getId(), LedgerEntryType.MERCHANT_PAYABLE)) {
            return;
        }
        BigDecimal commissionAmount = order.getPlatformFee()
                .multiply(BigDecimal.valueOf(order.getQuantity()))
                .min(order.getFinalAmount());
        BigDecimal merchantNet = order.getFinalAmount().subtract(commissionAmount);
        Instant availableAt = completedAt.plus(MERCHANT_HOLD_DAYS, ChronoUnit.DAYS);

        LedgerAccount escrow = account(LedgerOwnerType.PLATFORM, null, LedgerAccountType.ESCROW);
        LedgerAccount revenue = account(LedgerOwnerType.PLATFORM, null, LedgerAccountType.PLATFORM_REVENUE);
        LedgerAccount merchantPayable = account(
                LedgerOwnerType.MERCHANT,
                order.getStore().getBusinessProfile().getId(),
                LedgerAccountType.MERCHANT_PAYABLE);

        debit(escrow, order, payment, null, LedgerEntryType.MERCHANT_PAYABLE, order.getFinalAmount(), availableAt,
                "Release escrow for completed order " + order.getOrderNumber());
        credit(revenue, order, payment, null, LedgerEntryType.PLATFORM_COMMISSION, commissionAmount, availableAt,
                "Platform commission for " + order.getOrderNumber());
        credit(merchantPayable, order, payment, null, LedgerEntryType.MERCHANT_PAYABLE, merchantNet, availableAt,
                "Merchant payable for " + order.getOrderNumber());

        if (!commissionRepository.existsByOrderId(order.getId())) {
            commissionRepository.save(PlatformCommission.builder()
                    .order(order)
                    .payment(payment)
                    .grossAmount(order.getFinalAmount())
                    .platformFeeAmount(commissionAmount)
                    .merchantNetAmount(merchantNet)
                    .status(CommissionStatus.EARNED)
                    .build());
        }
    }

    @Transactional
    public void recordRefundApproved(RefundRequest refundRequest) {
        if (entryRepository.existsByOrderIdAndEntryType(refundRequest.getOrder().getId(), LedgerEntryType.REFUND_RESERVED)) {
            return;
        }
        BigDecimal amount = refundRequest.getApprovedAmount() == null
                ? refundRequest.getRequestedAmount()
                : refundRequest.getApprovedAmount();
        LedgerAccount refundLiability = account(LedgerOwnerType.PLATFORM, null, LedgerAccountType.REFUND_LIABILITY);
        credit(refundLiability, refundRequest.getOrder(), refundRequest.getPayment(), refundRequest,
                LedgerEntryType.REFUND_RESERVED, amount, Instant.now(), "Refund approved");
    }

    @Transactional
    public void recordRefundPaid(RefundRequest refundRequest, BigDecimal paidAmount) {
        Order order = refundRequest.getOrder();
        if (entryRepository.existsByOrderIdAndEntryType(order.getId(), LedgerEntryType.REFUND_PAID)) {
            return;
        }

        LedgerAccount cash = account(LedgerOwnerType.PLATFORM, null, LedgerAccountType.PLATFORM_CASH);
        LedgerAccount refundLiability = account(LedgerOwnerType.PLATFORM, null, LedgerAccountType.REFUND_LIABILITY);
        debit(refundLiability, order, refundRequest.getPayment(), refundRequest, LedgerEntryType.REFUND_PAID,
                paidAmount, Instant.now(), "Refund liability paid");
        debit(cash, order, refundRequest.getPayment(), refundRequest, LedgerEntryType.REFUND_PAID,
                paidAmount, Instant.now(), "Refund cash paid to customer");

        BigDecimal ratio = order.getFinalAmount().signum() == 0
                ? BigDecimal.ONE
                : paidAmount.divide(order.getFinalAmount(), 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        reverseEarnedEntry(order, refundRequest, LedgerEntryType.MERCHANT_PAYABLE, ratio,
                "Reverse merchant payable after refund");
        reverseEarnedEntry(order, refundRequest, LedgerEntryType.PLATFORM_COMMISSION, ratio,
                "Reverse platform commission after refund");
    }

    private void reverseEarnedEntry(Order order, RefundRequest refundRequest, LedgerEntryType type,
                                    BigDecimal ratio, String description) {
        entryRepository.findAllByOrderIdAndEntryType(order.getId(), type).stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.CREDIT)
                .forEach(entry -> {
                    BigDecimal amount = entry.getAmount().multiply(ratio).setScale(0, RoundingMode.HALF_UP);
                    if (amount.signum() > 0) {
                        debit(entry.getAccount(), order, refundRequest.getPayment(), refundRequest,
                                LedgerEntryType.REFUND_PAID, amount, Instant.now(), description);
                    }
                });
    }

    private LedgerAccount account(LedgerOwnerType ownerType, UUID ownerId, LedgerAccountType accountType) {
        return accountRepository.findByOwnerTypeAndOwnerIdAndAccountTypeAndCurrency(ownerType, ownerId, accountType, VND)
                .orElseGet(() -> accountRepository.save(LedgerAccount.builder()
                        .ownerType(ownerType)
                        .ownerId(ownerId)
                        .accountType(accountType)
                        .currency(VND)
                        .balance(BigDecimal.ZERO)
                        .build()));
    }

    private void credit(LedgerAccount account, Order order, Payment payment, RefundRequest refundRequest,
                        LedgerEntryType type, BigDecimal amount, Instant availableAt, String description) {
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        saveEntry(account, order, payment, refundRequest, type, LedgerEntryDirection.CREDIT, amount, availableAt, description);
    }

    private void debit(LedgerAccount account, Order order, Payment payment, RefundRequest refundRequest,
                       LedgerEntryType type, BigDecimal amount, Instant availableAt, String description) {
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        saveEntry(account, order, payment, refundRequest, type, LedgerEntryDirection.DEBIT, amount, availableAt, description);
    }

    private void saveEntry(LedgerAccount account, Order order, Payment payment, RefundRequest refundRequest,
                           LedgerEntryType type, LedgerEntryDirection direction, BigDecimal amount,
                           Instant availableAt, String description) {
        entryRepository.save(LedgerEntry.builder()
                .account(account)
                .order(order)
                .payment(payment)
                .refundRequest(refundRequest)
                .entryType(type)
                .direction(direction)
                .amount(amount)
                .currency(VND)
                .availableAt(availableAt)
                .description(description)
                .build());
    }
}
