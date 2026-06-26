package com.LastBite.modules.admin.dto;

import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.payment.enums.PaymentStatus;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record MetricCard(String key, String label, BigDecimal value, String unit, BigDecimal previousValue,
                             Double deltaPercent, String helper) {
    }

    public record DashboardSummaryResponse(LocalDate date, String timezone, BusinessHealth businessHealth,
                                           OperationsQueue operationsQueue, List<AlertItem> alerts,
                                           List<TopStoreMetric> topStores, TimeSeriesResponse gmvTrend,
                                           FunnelResponse funnel) {
    }

    public record BusinessHealth(BigDecimal gmvToday, BigDecimal gmvYesterday, Double gmvDeltaPercent,
                                 long paidOrdersToday, long completedOrdersToday, Double completionRate,
                                 long activeSellingStores) {
    }

    public record OperationsQueue(long pendingMerchants, long pendingStores, long pendingBankAccounts,
                                  long pendingRefunds, long openReviewReports, long storesUnderReview) {
    }

    public record AlertItem(String severity, String title, String message, String targetType, UUID targetId,
                            Instant detectedAt) {
    }

    public record ActionQueueResponse(LocalDate date, String timezone, List<ActionQueueItem> items) {
    }

    public record ActionQueueItem(String priority, String type, String title, String helper, String href,
                                  UUID targetId, Instant createdAt) {
    }

    public record TopStoreMetric(UUID storeId, String storeName, StoreCategory category, BigDecimal gmv,
                                 long orders, Double refundRate, Double conversionRate) {
    }

    public record FunnelResponse(List<FunnelStep> steps) {
    }

    public record FunnelStep(String key, String label, long count, Double percentOfPrevious, Double percentOfFirst) {
    }

    public record TimeSeriesResponse(RangeInfo range, String unit, Map<String, Object> summary,
                                     List<SeriesResponse> series) {
    }

    public record RangeInfo(Instant from, Instant to, String granularity, String timezone) {
    }

    public record SeriesResponse(String key, String label, List<ChartPoint> points) {
    }

    public record ChartPoint(Instant time, String label, BigDecimal value, Long count) {
    }

    public record AdminOrderListItem(UUID id, String orderNumber, OrderStatus status,
                                     OrderRefundStatus refundStatus, PaymentStatus paymentStatus,
                                     BigDecimal finalAmount, int quantity, UUID userId, String customerName,
                                     String customerEmail, UUID storeId, String storeName, UUID bagId,
                                     String bagName, LocalDate pickupDate, Instant paidAt, Instant createdAt) {
    }

    public record AdminOrderDetail(AdminOrderListItem order, List<AdminNoteResponse> notes) {
    }

    public record AdminUserListItem(UUID id, String email, String username, String fullName, String phone,
                                    AccountType accountType, UserStatus status, List<UserRole> roles,
                                    boolean emailVerified, boolean phoneVerified, Instant lastLoginAt,
                                    Instant createdAt, long paidOrders, BigDecimal totalGmv) {
    }

    public record AdminMerchantListItem(UUID id, UUID ownerId, String ownerName, String ownerEmail,
                                        String legalName, String representativeName, String representativePhone,
                                        String representativeEmail, ReviewStatus reviewStatus, long storeCount,
                                        BigDecimal gmv, long orders, Instant createdAt) {
    }

    public record AdminStoreListItem(UUID id, String name, String slug, StoreCategory category, StoreStatus status,
                                     VerificationStatus verificationStatus, String city, String district,
                                     String address, UUID merchantId, String merchantName, double avgRating,
                                     int totalRatings, BigDecimal gmv, long orders, boolean underReview,
                                     Instant suspendedUntil, Instant createdAt) {
    }

    public record StoreReliabilityResponse(UUID storeId, String storeName, int totalBagsListed, int totalBagsSold,
                                           int totalBagsFulfilled, int totalBagsNoShow, int merchantCancelledCount,
                                           int storeFaultRefundCount, double fulfillmentRate, int warningCount,
                                           boolean underReview, Instant suspendedUntil, Instant lastWarningAt,
                                           Instant lastRecalculatedAt) {
    }

    public record CategoryMetric(StoreCategory category, long orders, BigDecimal gmv, BigDecimal aov,
                                 Double sharePercent) {
    }

    public record RefundAnalyticsResponse(Instant from, Instant to, long totalRequests, long pendingRequests,
                                          long approvedRequests, long refundedRequests, BigDecimal requestedAmount,
                                          BigDecimal approvedAmount) {
    }

    public record UserAnalyticsResponse(Instant from, Instant to, long newUsers, long activeCustomers,
                                        long repeatCustomers, Double repeatPurchaseRate) {
    }

    public record FinanceReconciliationResponse(Instant from, Instant to, BigDecimal gmv, BigDecimal platformFees,
                                                BigDecimal refundsApproved, BigDecimal merchantNetEstimate) {
    }

    public record AdminNoteRequest(@NotBlank String note) {
    }

    public record AdminNoteResponse(UUID id, String targetType, UUID targetId, UUID actorId, String note,
                                    Instant createdAt) {
    }
}
