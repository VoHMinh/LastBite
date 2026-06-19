package com.LastBite.modules.store.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.refund.enums.RefundReason;
import com.LastBite.modules.refund.enums.RefundStatus;
import com.LastBite.modules.refund.repository.RefundRequestRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.entity.StoreReliabilityStats;
import com.LastBite.modules.store.repository.StoreReliabilityStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreReliabilityService {

    private static final Duration WARNING_WINDOW = Duration.ofDays(7);
    private static final Duration RATE_WINDOW = Duration.ofDays(30);
    private static final Duration SUSPENSION_DURATION = Duration.ofDays(7);
    private static final int WARNING_EVENT_THRESHOLD = 3;
    private static final int WARNING_MIN_PAID_ORDERS = 10;
    private static final int SUSPEND_MIN_PAID_ORDERS = 20;
    private static final double WARNING_FAULT_RATE = 0.10;
    private static final double SUSPEND_FAULT_RATE = 0.25;

    private static final Set<RefundReason> STORE_FAULT_REASONS = Set.of(
            RefundReason.STORE_CANCELLED,
            RefundReason.STORE_NO_STOCK,
            RefundReason.QUALITY_ISSUE,
            RefundReason.ALLERGEN_OR_LABELING,
            RefundReason.QUANTITY_SHORTAGE
    );
    private static final List<RefundStatus> COUNTED_REFUND_STATUSES = List.of(
            RefundStatus.APPROVED,
            RefundStatus.PROCESSING,
            RefundStatus.REFUNDED,
            RefundStatus.FAILED
    );

    private final StoreReliabilityStatsRepository statsRepository;
    private final RefundRequestRepository refundRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional
    public void recordOrderPaid(Order order) {
        StoreReliabilityStats stats = loadStats(order.getStore());
        stats.setTotalBagsSold(stats.getTotalBagsSold() + order.getQuantity());
        recalculateFulfillmentRate(stats);
        stats.setLastRecalculatedAt(Instant.now(clock));
    }

    @Transactional
    public void recordOrderFulfilled(Order order) {
        StoreReliabilityStats stats = loadStats(order.getStore());
        stats.setTotalBagsFulfilled(stats.getTotalBagsFulfilled() + order.getQuantity());
        recalculateFulfillmentRate(stats);
        stats.setLastRecalculatedAt(Instant.now(clock));
    }

    @Transactional
    public void recordCustomerNoShow(Order order) {
        StoreReliabilityStats stats = loadStats(order.getStore());
        stats.setTotalBagsNoShow(stats.getTotalBagsNoShow() + order.getQuantity());
        recalculateFulfillmentRate(stats);
        stats.setLastRecalculatedAt(Instant.now(clock));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags", "store-list", "store-detail", "store-by-slug"}, allEntries = true)
    public void recordStoreFaultRefund(Order order, RefundReason reason) {
        if (!isStoreFaultReason(reason)) {
            return;
        }
        StoreReliabilityStats stats = loadStats(order.getStore());
        if (reason == RefundReason.STORE_CANCELLED || reason == RefundReason.STORE_NO_STOCK) {
            stats.setMerchantCancelledCount(stats.getMerchantCancelledCount() + order.getQuantity());
        } else {
            stats.setStoreFaultRefundCount(stats.getStoreFaultRefundCount() + order.getQuantity());
        }
        recalculateEnforcement(stats);
    }

    @Transactional(readOnly = true)
    public void ensureStoreCanReceiveOrders(Store store) {
        statsRepository.findById(store.getId()).ifPresent(stats -> {
            Instant suspendedUntil = stats.getSuspendedUntil();
            if (suspendedUntil != null && suspendedUntil.isAfter(Instant.now(clock))) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Cua hang dang bi tam ngung nhan don do chi so reliability");
            }
        });
    }

    private StoreReliabilityStats loadStats(Store store) {
        return statsRepository.findByStoreIdForUpdate(store.getId())
                .orElseGet(() -> statsRepository.save(StoreReliabilityStats.builder()
                        .storeId(store.getId())
                        .store(store)
                        .build()));
    }

    private void recalculateFulfillmentRate(StoreReliabilityStats stats) {
        if (stats.getTotalBagsSold() <= 0) {
            stats.setFulfillmentRate(1.0);
            return;
        }
        stats.setFulfillmentRate((double) stats.getTotalBagsFulfilled() / stats.getTotalBagsSold());
    }

    private void recalculateEnforcement(StoreReliabilityStats stats) {
        Instant now = Instant.now(clock);
        long recentFaults = refundRepository.countStoreFaultRefundsSince(
                stats.getStoreId(), STORE_FAULT_REASONS, COUNTED_REFUND_STATUSES, now.minus(WARNING_WINDOW));
        long paidOrders = orderRepository.countPaidOrdersSince(stats.getStoreId(), now.minus(RATE_WINDOW));
        long faultOrders = refundRepository.countStoreFaultRefundsSince(
                stats.getStoreId(), STORE_FAULT_REASONS, COUNTED_REFUND_STATUSES, now.minus(RATE_WINDOW));

        double faultRate = paidOrders == 0 ? 0 : (double) faultOrders / paidOrders;
        boolean shouldWarn = recentFaults >= WARNING_EVENT_THRESHOLD
                || (paidOrders >= WARNING_MIN_PAID_ORDERS && faultRate >= WARNING_FAULT_RATE);
        if (shouldWarn && canIssueWarning(stats.getLastWarningAt(), now)) {
            stats.setWarningCount(stats.getWarningCount() + 1);
            stats.setLastWarningAt(now);
        }

        if (stats.getWarningCount() >= 2) {
            stats.setUnderReview(true);
        }

        boolean shouldSuspend = stats.getWarningCount() >= 3
                || (paidOrders >= SUSPEND_MIN_PAID_ORDERS && faultRate >= SUSPEND_FAULT_RATE);
        if (shouldSuspend) {
            Instant newSuspensionEnd = now.plus(SUSPENSION_DURATION);
            if (stats.getSuspendedUntil() == null || stats.getSuspendedUntil().isBefore(newSuspensionEnd)) {
                stats.setSuspendedUntil(newSuspensionEnd);
            }
            stats.setUnderReview(true);
        }
        stats.setLastRecalculatedAt(now);
    }

    private boolean canIssueWarning(Instant lastWarningAt, Instant now) {
        return lastWarningAt == null || !lastWarningAt.plus(WARNING_WINDOW).isAfter(now);
    }

    private boolean isStoreFaultReason(RefundReason reason) {
        return STORE_FAULT_REASONS.contains(reason);
    }
}
