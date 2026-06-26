package com.LastBite.modules.admin.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.admin.dto.AdminDtos.*;
import com.LastBite.modules.admin.entity.AdminNote;
import com.LastBite.modules.admin.repository.AdminNoteRepository;
import com.LastBite.modules.auth.entity.Role;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.merchant.enums.BankAccountVerificationStatus;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.payment.enums.PaymentStatus;
import com.LastBite.modules.refund.enums.RefundStatus;
import com.LastBite.modules.review.enums.ReviewReportStatus;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.entity.StoreReliabilityStats;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReadService {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<OrderStatus> COMPLETED = List.of(OrderStatus.PICKED_UP);
    private static final List<RefundStatus> APPROVED_REFUNDS = List.of(RefundStatus.APPROVED, RefundStatus.PROCESSING, RefundStatus.REFUNDED);

    @PersistenceContext
    private EntityManager em;
    private final AdminNoteRepository noteRepository;

    public DashboardSummaryResponse dashboardSummary(LocalDate date, String timezone) {
        ZoneId zone = zone(timezone);
        LocalDate day = date != null ? date : LocalDate.now(zone);
        Instant from = start(day, zone);
        Instant to = start(day.plusDays(1), zone);
        Instant previousFrom = start(day.minusDays(1), zone);
        BigDecimal gmv = paidGmv(from, to);
        BigDecimal previousGmv = paidGmv(previousFrom, from);
        long paidOrders = count("SELECT COUNT(o) FROM Order o WHERE o.paidAt >= :from AND o.paidAt < :to", Map.of("from", from, "to", to));
        long completedOrders = count("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses AND o.createdAt >= :from AND o.createdAt < :to", Map.of("statuses", COMPLETED, "from", from, "to", to));
        long activeStores = count("SELECT COUNT(DISTINCT o.store.id) FROM Order o WHERE o.paidAt >= :from AND o.paidAt < :to", Map.of("from", from, "to", to));
        BusinessHealth health = new BusinessHealth(gmv, previousGmv, delta(gmv, previousGmv), paidOrders, completedOrders, percent(completedOrders, paidOrders), activeStores);
        OperationsQueue queue = operationsQueue();
        return new DashboardSummaryResponse(day, zone.getId(), health, queue, alerts(day, health, queue), topStores(from, to, 5), salesSeries(day.minusDays(6), day, zone.getId()), funnel(from, to));
    }

    public ActionQueueResponse actionQueue(LocalDate date, String timezone) {
        ZoneId zone = zone(timezone);
        LocalDate day = date != null ? date : LocalDate.now(zone);
        List<ActionQueueItem> items = new ArrayList<>();
        em.createQuery("SELECT p FROM MerchantBusinessProfile p JOIN FETCH p.owner WHERE p.reviewStatus IN :statuses ORDER BY p.createdAt ASC", com.LastBite.modules.merchant.entity.MerchantBusinessProfile.class)
                .setParameter("statuses", List.of(ReviewStatus.PENDING_REVIEW, ReviewStatus.CHANGES_REQUESTED)).setMaxResults(8).getResultList()
                .forEach(p -> items.add(new ActionQueueItem("HIGH", "MERCHANT_REVIEW", safe(p.getLegalName(), p.getRepresentativeFullName()) + " can duyet", String.valueOf(p.getReviewStatus()), "/admin/merchants/" + p.getId(), p.getId(), p.getCreatedAt())));
        em.createQuery("SELECT s FROM Store s WHERE s.verificationStatus IN :statuses ORDER BY s.createdAt ASC", Store.class)
                .setParameter("statuses", List.of(VerificationStatus.PENDING, VerificationStatus.CHANGES_REQUESTED)).setMaxResults(8).getResultList()
                .forEach(s -> items.add(new ActionQueueItem("HIGH", "STORE_REVIEW", s.getName() + " can xac minh", safe(s.getDistrict(), s.getCity()), "/admin/stores/" + s.getId(), s.getId(), s.getCreatedAt())));
        em.createQuery("SELECT r FROM RefundRequest r JOIN FETCH r.order o WHERE r.status = :status ORDER BY r.createdAt ASC", com.LastBite.modules.refund.entity.RefundRequest.class)
                .setParameter("status", RefundStatus.PENDING_REVIEW).setMaxResults(8).getResultList()
                .forEach(r -> items.add(new ActionQueueItem("URGENT", "REFUND_REVIEW", "Hoan tien " + r.getOrder().getOrderNumber(), money(r.getRequestedAmount()), "/admin/orders/" + r.getOrder().getId(), r.getOrder().getId(), r.getCreatedAt())));
        return new ActionQueueResponse(day, zone.getId(), items.stream().sorted(Comparator.comparing(ActionQueueItem::createdAt)).limit(25).toList());
    }

    public TimeSeriesResponse salesSeries(LocalDate fromDate, LocalDate toDate, String timezone) {
        ZoneId zone = zone(timezone);
        LocalDate fromDay = fromDate != null ? fromDate : LocalDate.now(zone).minusDays(29);
        LocalDate toDay = toDate != null ? toDate : LocalDate.now(zone);
        if (toDay.isBefore(fromDay)) toDay = fromDay;
        Instant from = start(fromDay, zone);
        Instant to = start(toDay.plusDays(1), zone);
        long days = Duration.between(fromDay.atStartOfDay(), toDay.plusDays(1).atStartOfDay()).toDays();
        Instant previousFrom = start(fromDay.minusDays(days), zone);
        List<ChartPoint> current = aggregatePaid(from, to, zone, fromDay, toDay);
        List<ChartPoint> previous = aggregatePaid(previousFrom, from, zone, fromDay.minusDays(days), fromDay.minusDays(1));
        BigDecimal total = current.stream().map(ChartPoint::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        long orders = current.stream().map(ChartPoint::count).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        return new TimeSeriesResponse(new RangeInfo(from, to, "day", zone.getId()), "VND", Map.of("totalGmv", total, "paidOrders", orders, "points", current.size()), List.of(new SeriesResponse("current", "Ky hien tai", current), new SeriesResponse("previous", "Ky truoc", previous)));
    }

    public FunnelResponse funnel(Instant from, Instant to) {
        Instant actualFrom = from != null ? from : start(LocalDate.now(DEFAULT_ZONE), DEFAULT_ZONE);
        Instant actualTo = to != null ? to : actualFrom.plus(Duration.ofDays(1));
        long created = count("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :from AND o.createdAt < :to", Map.of("from", actualFrom, "to", actualTo));
        long paid = count("SELECT COUNT(o) FROM Order o WHERE o.paidAt >= :from AND o.paidAt < :to", Map.of("from", actualFrom, "to", actualTo));
        long ready = count("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses AND o.createdAt >= :from AND o.createdAt < :to", Map.of("statuses", List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.PICKED_UP), "from", actualFrom, "to", actualTo));
        long completed = count("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses AND o.createdAt >= :from AND o.createdAt < :to", Map.of("statuses", COMPLETED, "from", actualFrom, "to", actualTo));
        return new FunnelResponse(List.of(step("created", "Tao don", created, created, created), step("paid", "Da thanh toan", paid, created, created), step("ready", "San sang nhan", ready, paid, created), step("completed", "Da hoan tat", completed, ready, created)));
    }

    public List<TopStoreMetric> topStores(Instant from, Instant to, int limit) {
        Instant actualFrom = from != null ? from : Instant.now().minus(Duration.ofDays(30));
        Instant actualTo = to != null ? to : Instant.now();
        List<Object[]> rows = em.createQuery("SELECT s.id, s.name, s.category, COALESCE(SUM(o.finalAmount),0), COUNT(o.id) FROM Order o JOIN o.store s WHERE o.paidAt >= :from AND o.paidAt < :to GROUP BY s.id, s.name, s.category ORDER BY COALESCE(SUM(o.finalAmount),0) DESC", Object[].class)
                .setParameter("from", actualFrom).setParameter("to", actualTo).setMaxResults(Math.min(Math.max(limit, 1), 50)).getResultList();
        return rows.stream().map(row -> {
            UUID storeId = (UUID) row[0];
            long orders = toLong(row[4]);
            long refunds = count("SELECT COUNT(r) FROM RefundRequest r WHERE r.order.store.id = :storeId AND r.createdAt >= :from AND r.createdAt < :to", Map.of("storeId", storeId, "from", actualFrom, "to", actualTo));
            return new TopStoreMetric(storeId, (String) row[1], (StoreCategory) row[2], bd(row[3]), orders, percent(refunds, orders), null);
        }).toList();
    }

    public List<CategoryMetric> categoryMetrics(Instant from, Instant to) {
        Instant actualFrom = from != null ? from : Instant.now().minus(Duration.ofDays(30));
        Instant actualTo = to != null ? to : Instant.now();
        List<Object[]> rows = em.createQuery("SELECT s.category, COUNT(o.id), COALESCE(SUM(o.finalAmount),0) FROM Order o JOIN o.store s WHERE o.paidAt >= :from AND o.paidAt < :to GROUP BY s.category ORDER BY COUNT(o.id) DESC", Object[].class)
                .setParameter("from", actualFrom).setParameter("to", actualTo).getResultList();
        long totalOrders = rows.stream().mapToLong(row -> toLong(row[1])).sum();
        return rows.stream().map(row -> {
            long orders = toLong(row[1]);
            BigDecimal gmv = bd(row[2]);
            BigDecimal aov = orders == 0 ? BigDecimal.ZERO : gmv.divide(BigDecimal.valueOf(orders), 0, RoundingMode.HALF_UP);
            return new CategoryMetric((StoreCategory) row[0], orders, gmv, aov, percent(orders, totalOrders));
        }).toList();
    }

    public PageResponse<AdminOrderListItem> searchOrders(OrderStatus status, PaymentStatus paymentStatus, OrderRefundStatus refundStatus, UUID storeId, UUID customerId, Instant from, Instant to, String keyword, Pageable pageable) {
        StringBuilder jpql = new StringBuilder("SELECT o FROM Order o JOIN FETCH o.user JOIN FETCH o.store JOIN FETCH o.bag WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        orderFilters(jpql, params, status, refundStatus, storeId, customerId, from, to, keyword);
        if (paymentStatus != null) { jpql.append(" AND EXISTS (SELECT p.id FROM Payment p WHERE p.order = o AND p.status = :paymentStatus)"); params.put("paymentStatus", paymentStatus); }
        jpql.append(" ORDER BY o.createdAt DESC");
        List<Order> orders = pageQuery(jpql.toString(), params, Order.class, pageable);
        long total = countOrders(status, paymentStatus, refundStatus, storeId, customerId, from, to, keyword);
        return page(orders.stream().map(this::toOrder).toList(), pageable, total);
    }

    public AdminOrderDetail orderDetail(UUID orderId) {
        Order order = em.createQuery("SELECT o FROM Order o JOIN FETCH o.user JOIN FETCH o.store JOIN FETCH o.bag WHERE o.id = :id", Order.class)
                .setParameter("id", orderId).getResultStream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        List<AdminNoteResponse> notes = noteRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("ORDER", orderId).stream().map(this::toNote).toList();
        return new AdminOrderDetail(toOrder(order), notes);
    }

    @Transactional
    public AdminNoteResponse addNote(String targetType, UUID targetId, UUID actorId, String note) {
        User actor = em.find(User.class, actorId);
        if (actor == null) throw new ApiException(ErrorCode.USER_NOT_FOUND);
        AdminNote saved = noteRepository.save(AdminNote.builder().targetType(targetType.toUpperCase(Locale.ROOT)).targetId(targetId).actor(actor).note(note.trim()).build());
        return toNote(saved);
    }

    public PageResponse<AdminUserListItem> searchUsers(UserRole role, UserStatus status, String keyword, Instant from, Instant to, Pageable pageable) {
        StringBuilder jpql = new StringBuilder("SELECT DISTINCT u FROM User u LEFT JOIN u.roles r WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        userFilters(jpql, params, role, status, keyword, from, to);
        jpql.append(" ORDER BY u.createdAt DESC");
        List<User> users = pageQuery(jpql.toString(), params, User.class, pageable);
        long total = countUsers(role, status, keyword, from, to);
        return page(users.stream().map(this::toUser).toList(), pageable, total);
    }

    public AdminUserListItem userDetail(UUID userId) {
        User user = em.find(User.class, userId);
        if (user == null) throw new ApiException(ErrorCode.USER_NOT_FOUND);
        return toUser(user);
    }

    public Map<String, Object> userActivity(UUID userId) {
        userDetail(userId);
        long orders = count("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId", Map.of("userId", userId));
        long paidOrders = count("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.paidAt IS NOT NULL", Map.of("userId", userId));
        BigDecimal totalGmv = sum("SELECT COALESCE(SUM(o.finalAmount),0) FROM Order o WHERE o.user.id = :userId AND o.paidAt IS NOT NULL", Map.of("userId", userId));
        Instant lastOrderAt = em.createQuery("SELECT MAX(o.createdAt) FROM Order o WHERE o.user.id = :userId", Instant.class).setParameter("userId", userId).getSingleResult();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", orders); result.put("paidOrders", paidOrders); result.put("totalGmv", totalGmv); result.put("lastOrderAt", lastOrderAt);
        return result;
    }

    public PageResponse<AdminMerchantListItem> searchMerchants(ReviewStatus status, String keyword, Pageable pageable) {
        StringBuilder jpql = new StringBuilder("SELECT p FROM MerchantBusinessProfile p JOIN FETCH p.owner WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (status != null) { jpql.append(" AND p.reviewStatus = :status"); params.put("status", status); }
        if (text(keyword)) { jpql.append(" AND (LOWER(p.legalName) LIKE :keyword OR LOWER(p.representativeFullName) LIKE :keyword OR LOWER(p.owner.email) LIKE :keyword)"); params.put("keyword", like(keyword)); }
        jpql.append(" ORDER BY p.createdAt DESC");
        var rows = pageQuery(jpql.toString(), params, com.LastBite.modules.merchant.entity.MerchantBusinessProfile.class, pageable);
        long total = countMerchants(status, keyword);
        return page(rows.stream().map(this::toMerchant).toList(), pageable, total);
    }

    public AdminMerchantListItem merchantDetail(UUID merchantId) {
        var merchant = em.createQuery("SELECT p FROM MerchantBusinessProfile p JOIN FETCH p.owner WHERE p.id = :id", com.LastBite.modules.merchant.entity.MerchantBusinessProfile.class)
                .setParameter("id", merchantId).getResultStream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay merchant"));
        return toMerchant(merchant);
    }

    public PageResponse<AdminStoreListItem> searchStores(StoreStatus status, VerificationStatus verificationStatus, StoreCategory category, String keyword, Pageable pageable) {
        StringBuilder jpql = new StringBuilder("SELECT s FROM Store s JOIN FETCH s.businessProfile p WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (status != null) { jpql.append(" AND s.status = :status"); params.put("status", status); }
        if (verificationStatus != null) { jpql.append(" AND s.verificationStatus = :verificationStatus"); params.put("verificationStatus", verificationStatus); }
        if (category != null) { jpql.append(" AND s.category = :category"); params.put("category", category); }
        if (text(keyword)) { jpql.append(" AND (LOWER(s.name) LIKE :keyword OR LOWER(s.address) LIKE :keyword OR LOWER(s.district) LIKE :keyword)"); params.put("keyword", like(keyword)); }
        jpql.append(" ORDER BY s.createdAt DESC");
        List<Store> stores = pageQuery(jpql.toString(), params, Store.class, pageable);
        long total = countStores(status, verificationStatus, category, keyword);
        return page(stores.stream().map(this::toStore).toList(), pageable, total);
    }

    public StoreReliabilityResponse storeReliability(UUID storeId) {
        StoreReliabilityStats stats = em.find(StoreReliabilityStats.class, storeId);
        if (stats == null) {
            Store store = em.find(Store.class, storeId);
            if (store == null) throw new ApiException(ErrorCode.STORE_NOT_FOUND);
            return new StoreReliabilityResponse(store.getId(), store.getName(), 0, 0, 0, 0, 0, 0, 0, 0, false, null, null, null);
        }
        Store store = stats.getStore();
        return new StoreReliabilityResponse(store.getId(), store.getName(), stats.getTotalBagsListed(), stats.getTotalBagsSold(), stats.getTotalBagsFulfilled(), stats.getTotalBagsNoShow(), stats.getMerchantCancelledCount(), stats.getStoreFaultRefundCount(), stats.getFulfillmentRate(), stats.getWarningCount(), stats.isUnderReview(), stats.getSuspendedUntil(), stats.getLastWarningAt(), stats.getLastRecalculatedAt());
    }

    public List<StoreReliabilityResponse> reliabilityWatchlist(int limit) {
        return em.createQuery("SELECT rs FROM StoreReliabilityStats rs JOIN FETCH rs.store WHERE rs.underReview = true OR rs.warningCount > 0 OR rs.fulfillmentRate < 0.9 ORDER BY rs.underReview DESC, rs.warningCount DESC, rs.fulfillmentRate ASC", StoreReliabilityStats.class)
                .setMaxResults(Math.min(Math.max(limit, 1), 100)).getResultList().stream()
                .map(rs -> new StoreReliabilityResponse(rs.getStore().getId(), rs.getStore().getName(), rs.getTotalBagsListed(), rs.getTotalBagsSold(), rs.getTotalBagsFulfilled(), rs.getTotalBagsNoShow(), rs.getMerchantCancelledCount(), rs.getStoreFaultRefundCount(), rs.getFulfillmentRate(), rs.getWarningCount(), rs.isUnderReview(), rs.getSuspendedUntil(), rs.getLastWarningAt(), rs.getLastRecalculatedAt()))
                .toList();
    }

    public RefundAnalyticsResponse refundAnalytics(Instant from, Instant to) {
        Instant actualFrom = from != null ? from : Instant.now().minus(Duration.ofDays(30));
        Instant actualTo = to != null ? to : Instant.now();
        long total = count("SELECT COUNT(r) FROM RefundRequest r WHERE r.createdAt >= :from AND r.createdAt < :to", Map.of("from", actualFrom, "to", actualTo));
        long pending = count("SELECT COUNT(r) FROM RefundRequest r WHERE r.status = :status AND r.createdAt >= :from AND r.createdAt < :to", Map.of("status", RefundStatus.PENDING_REVIEW, "from", actualFrom, "to", actualTo));
        long approved = count("SELECT COUNT(r) FROM RefundRequest r WHERE r.status IN :statuses AND r.createdAt >= :from AND r.createdAt < :to", Map.of("statuses", APPROVED_REFUNDS, "from", actualFrom, "to", actualTo));
        long refunded = count("SELECT COUNT(r) FROM RefundRequest r WHERE r.status = :status AND r.createdAt >= :from AND r.createdAt < :to", Map.of("status", RefundStatus.REFUNDED, "from", actualFrom, "to", actualTo));
        BigDecimal requested = sum("SELECT COALESCE(SUM(r.requestedAmount),0) FROM RefundRequest r WHERE r.createdAt >= :from AND r.createdAt < :to", Map.of("from", actualFrom, "to", actualTo));
        BigDecimal approvedAmount = sum("SELECT COALESCE(SUM(r.approvedAmount),0) FROM RefundRequest r WHERE r.status IN :statuses AND r.createdAt >= :from AND r.createdAt < :to", Map.of("statuses", APPROVED_REFUNDS, "from", actualFrom, "to", actualTo));
        return new RefundAnalyticsResponse(actualFrom, actualTo, total, pending, approved, refunded, requested, approvedAmount);
    }

    public UserAnalyticsResponse userAnalytics(Instant from, Instant to) {
        Instant actualFrom = from != null ? from : Instant.now().minus(Duration.ofDays(30));
        Instant actualTo = to != null ? to : Instant.now();
        long newUsers = count("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt < :to", Map.of("from", actualFrom, "to", actualTo));
        long activeCustomers = count("SELECT COUNT(DISTINCT o.user.id) FROM Order o WHERE o.paidAt >= :from AND o.paidAt < :to", Map.of("from", actualFrom, "to", actualTo));
        long repeatCustomers = em.createQuery("SELECT o.user.id FROM Order o WHERE o.paidAt IS NOT NULL GROUP BY o.user.id HAVING COUNT(o.id) >= 2", UUID.class).getResultList().size();
        return new UserAnalyticsResponse(actualFrom, actualTo, newUsers, activeCustomers, repeatCustomers, percent(repeatCustomers, activeCustomers));
    }

    public FinanceReconciliationResponse financeReconciliation(Instant from, Instant to) {
        Instant actualFrom = from != null ? from : Instant.now().minus(Duration.ofDays(30));
        Instant actualTo = to != null ? to : Instant.now();
        List<Order> orders = paidOrders(actualFrom, actualTo);
        BigDecimal gmv = orders.stream().map(Order::getFinalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fees = orders.stream().map(o -> nvl(o.getPlatformFee()).multiply(BigDecimal.valueOf(o.getQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunds = sum("SELECT COALESCE(SUM(r.approvedAmount),0) FROM RefundRequest r WHERE r.status IN :statuses AND r.createdAt >= :from AND r.createdAt < :to", Map.of("statuses", APPROVED_REFUNDS, "from", actualFrom, "to", actualTo));
        return new FinanceReconciliationResponse(actualFrom, actualTo, gmv, fees, refunds, gmv.subtract(fees).subtract(refunds));
    }

    private OperationsQueue operationsQueue() {
        long pendingMerchants = count("SELECT COUNT(p) FROM MerchantBusinessProfile p WHERE p.reviewStatus = :status", Map.of("status", ReviewStatus.PENDING_REVIEW));
        long pendingStores = count("SELECT COUNT(s) FROM Store s WHERE s.verificationStatus = :status", Map.of("status", VerificationStatus.PENDING));
        long pendingBankAccounts = count("SELECT COUNT(a) FROM MerchantBankAccount a WHERE a.verificationStatus = :status", Map.of("status", BankAccountVerificationStatus.PENDING_REVIEW));
        long pendingRefunds = count("SELECT COUNT(r) FROM RefundRequest r WHERE r.status = :status", Map.of("status", RefundStatus.PENDING_REVIEW));
        long openReviewReports = count("SELECT COUNT(r) FROM ReviewReport r WHERE r.status = :status", Map.of("status", ReviewReportStatus.PENDING));
        long storesUnderReview = count("SELECT COUNT(rs) FROM StoreReliabilityStats rs WHERE rs.underReview = true", Map.of());
        return new OperationsQueue(pendingMerchants, pendingStores, pendingBankAccounts, pendingRefunds, openReviewReports, storesUnderReview);
    }

    private List<AlertItem> alerts(LocalDate day, BusinessHealth health, OperationsQueue queue) {
        List<AlertItem> alerts = new ArrayList<>();
        if (health.gmvToday().compareTo(BigDecimal.ZERO) == 0) alerts.add(new AlertItem("CRITICAL", "Chua co GMV hom nay", "Khong co don da thanh toan trong ngay " + day, "DASHBOARD", null, Instant.now()));
        if (health.gmvDeltaPercent() != null && health.gmvDeltaPercent() <= -20) alerts.add(new AlertItem("HIGH", "GMV giam manh", "GMV giam " + Math.abs(health.gmvDeltaPercent()) + "% so voi hom qua", "DASHBOARD", null, Instant.now()));
        if (queue.pendingRefunds() > 0) alerts.add(new AlertItem("HIGH", "Refund can duyet", queue.pendingRefunds() + " yeu cau hoan tien dang cho", "REFUND", null, Instant.now()));
        if (queue.storesUnderReview() > 0) alerts.add(new AlertItem("MEDIUM", "Cua hang trong watchlist", queue.storesUnderReview() + " cua hang dang bi theo doi do tin cay", "STORE", null, Instant.now()));
        return alerts;
    }

    private List<ChartPoint> aggregatePaid(Instant from, Instant to, ZoneId zone, LocalDate startDay, LocalDate endDay) {
        Map<LocalDate, List<Order>> byDate = paidOrders(from, to).stream().collect(Collectors.groupingBy(o -> LocalDateTime.ofInstant(o.getPaidAt(), zone).toLocalDate()));
        List<ChartPoint> points = new ArrayList<>();
        for (LocalDate day = startDay; !day.isAfter(endDay); day = day.plusDays(1)) {
            List<Order> orders = byDate.getOrDefault(day, List.of());
            BigDecimal value = orders.stream().map(Order::getFinalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new ChartPoint(start(day, zone), day.format(DateTimeFormatter.ofPattern("dd/MM")), value, (long) orders.size()));
        }
        return points;
    }

    private List<Order> paidOrders(Instant from, Instant to) {
        return em.createQuery("SELECT o FROM Order o JOIN FETCH o.store JOIN FETCH o.user JOIN FETCH o.bag WHERE o.paidAt >= :from AND o.paidAt < :to", Order.class).setParameter("from", from).setParameter("to", to).getResultList();
    }

    private AdminOrderListItem toOrder(Order order) {
        PaymentStatus paymentStatus = em.createQuery("SELECT p.status FROM Payment p WHERE p.order.id = :id", PaymentStatus.class).setParameter("id", order.getId()).getResultStream().findFirst().orElse(null);
        return new AdminOrderListItem(order.getId(), order.getOrderNumber(), order.getStatus(), order.getRefundStatus(), paymentStatus, order.getFinalAmount(), order.getQuantity(), order.getUser().getId(), order.getUser().getFullName(), order.getUser().getEmail(), order.getStore().getId(), order.getStore().getName(), order.getBag().getId(), order.getBag().getName(), order.getPickupDate(), order.getPaidAt(), order.getCreatedAt());
    }

    private AdminUserListItem toUser(User user) {
        long paidOrders = count("SELECT COUNT(o) FROM Order o WHERE o.user.id = :id AND o.paidAt IS NOT NULL", Map.of("id", user.getId()));
        BigDecimal totalGmv = sum("SELECT COALESCE(SUM(o.finalAmount),0) FROM Order o WHERE o.user.id = :id AND o.paidAt IS NOT NULL", Map.of("id", user.getId()));
        List<UserRole> roles = user.getRoles().stream().map(Role::getCode).sorted(Comparator.comparing(Enum::name)).toList();
        return new AdminUserListItem(user.getId(), user.getEmail(), user.getUsername(), user.getFullName(), user.getPhone(), user.getAccountType(), user.getStatus(), roles, user.isEmailVerified(), user.isPhoneVerified(), user.getLastLoginAt(), user.getCreatedAt(), paidOrders, totalGmv);
    }

    private AdminMerchantListItem toMerchant(com.LastBite.modules.merchant.entity.MerchantBusinessProfile merchant) {
        UUID id = merchant.getId();
        long stores = count("SELECT COUNT(s) FROM Store s WHERE s.businessProfile.id = :id", Map.of("id", id));
        long orders = count("SELECT COUNT(o) FROM Order o WHERE o.store.businessProfile.id = :id AND o.paidAt IS NOT NULL", Map.of("id", id));
        BigDecimal gmv = sum("SELECT COALESCE(SUM(o.finalAmount),0) FROM Order o WHERE o.store.businessProfile.id = :id AND o.paidAt IS NOT NULL", Map.of("id", id));
        User owner = merchant.getOwner();
        return new AdminMerchantListItem(id, owner.getId(), owner.getFullName(), owner.getEmail(), merchant.getLegalName(), merchant.getRepresentativeFullName(), merchant.getRepresentativePhone(), merchant.getRepresentativeEmail(), merchant.getReviewStatus(), stores, gmv, orders, merchant.getCreatedAt());
    }

    private AdminStoreListItem toStore(Store store) {
        UUID id = store.getId();
        long orders = count("SELECT COUNT(o) FROM Order o WHERE o.store.id = :id AND o.paidAt IS NOT NULL", Map.of("id", id));
        BigDecimal gmv = sum("SELECT COALESCE(SUM(o.finalAmount),0) FROM Order o WHERE o.store.id = :id AND o.paidAt IS NOT NULL", Map.of("id", id));
        StoreReliabilityStats stats = em.find(StoreReliabilityStats.class, id);
        return new AdminStoreListItem(id, store.getName(), store.getSlug(), store.getCategory(), store.getStatus(), store.getVerificationStatus(), store.getCity(), store.getDistrict(), store.getAddress(), store.getBusinessProfile().getId(), store.getBusinessProfile().getLegalName(), store.getAvgRating(), store.getTotalRatings(), gmv, orders, stats != null && stats.isUnderReview(), stats == null ? null : stats.getSuspendedUntil(), store.getCreatedAt());
    }

    private AdminNoteResponse toNote(AdminNote note) {
        return new AdminNoteResponse(note.getId(), note.getTargetType(), note.getTargetId(), note.getActor().getId(), note.getNote(), note.getCreatedAt());
    }

    private void orderFilters(StringBuilder jpql, Map<String, Object> params, OrderStatus status, OrderRefundStatus refundStatus, UUID storeId, UUID customerId, Instant from, Instant to, String keyword) {
        if (status != null) { jpql.append(" AND o.status = :status"); params.put("status", status); }
        if (refundStatus != null) { jpql.append(" AND o.refundStatus = :refundStatus"); params.put("refundStatus", refundStatus); }
        if (storeId != null) { jpql.append(" AND o.store.id = :storeId"); params.put("storeId", storeId); }
        if (customerId != null) { jpql.append(" AND o.user.id = :customerId"); params.put("customerId", customerId); }
        if (from != null) { jpql.append(" AND o.createdAt >= :from"); params.put("from", from); }
        if (to != null) { jpql.append(" AND o.createdAt < :to"); params.put("to", to); }
        if (text(keyword)) { jpql.append(" AND (LOWER(o.orderNumber) LIKE :keyword OR LOWER(o.user.email) LIKE :keyword OR LOWER(o.user.fullName) LIKE :keyword OR LOWER(o.store.name) LIKE :keyword)"); params.put("keyword", like(keyword)); }
    }

    private void userFilters(StringBuilder jpql, Map<String, Object> params, UserRole role, UserStatus status, String keyword, Instant from, Instant to) {
        if (role != null) { jpql.append(" AND r.code = :role"); params.put("role", role); }
        if (status != null) { jpql.append(" AND u.status = :status"); params.put("status", status); }
        if (from != null) { jpql.append(" AND u.createdAt >= :from"); params.put("from", from); }
        if (to != null) { jpql.append(" AND u.createdAt < :to"); params.put("to", to); }
        if (text(keyword)) { jpql.append(" AND (LOWER(u.email) LIKE :keyword OR LOWER(u.fullName) LIKE :keyword OR LOWER(u.phone) LIKE :keyword)"); params.put("keyword", like(keyword)); }
    }

    private long countOrders(OrderStatus status, PaymentStatus paymentStatus, OrderRefundStatus refundStatus, UUID storeId, UUID customerId, Instant from, Instant to, String keyword) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(o) FROM Order o WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        orderFilters(jpql, params, status, refundStatus, storeId, customerId, from, to, keyword);
        if (paymentStatus != null) { jpql.append(" AND EXISTS (SELECT p.id FROM Payment p WHERE p.order = o AND p.status = :paymentStatus)"); params.put("paymentStatus", paymentStatus); }
        return count(jpql.toString(), params);
    }

    private long countUsers(UserRole role, UserStatus status, String keyword, Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(DISTINCT u) FROM User u LEFT JOIN u.roles r WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        userFilters(jpql, params, role, status, keyword, from, to);
        return count(jpql.toString(), params);
    }

    private long countMerchants(ReviewStatus status, String keyword) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(p) FROM MerchantBusinessProfile p WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (status != null) { jpql.append(" AND p.reviewStatus = :status"); params.put("status", status); }
        if (text(keyword)) { jpql.append(" AND (LOWER(p.legalName) LIKE :keyword OR LOWER(p.representativeFullName) LIKE :keyword OR LOWER(p.owner.email) LIKE :keyword)"); params.put("keyword", like(keyword)); }
        return count(jpql.toString(), params);
    }

    private long countStores(StoreStatus status, VerificationStatus verificationStatus, StoreCategory category, String keyword) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(s) FROM Store s WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (status != null) { jpql.append(" AND s.status = :status"); params.put("status", status); }
        if (verificationStatus != null) { jpql.append(" AND s.verificationStatus = :verificationStatus"); params.put("verificationStatus", verificationStatus); }
        if (category != null) { jpql.append(" AND s.category = :category"); params.put("category", category); }
        if (text(keyword)) { jpql.append(" AND (LOWER(s.name) LIKE :keyword OR LOWER(s.address) LIKE :keyword OR LOWER(s.district) LIKE :keyword)"); params.put("keyword", like(keyword)); }
        return count(jpql.toString(), params);
    }

    private <T> List<T> pageQuery(String jpql, Map<String, Object> params, Class<T> type, Pageable pageable) {
        var query = em.createQuery(jpql, type);
        params.forEach(query::setParameter);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return query.getResultList();
    }

    private <T> PageResponse<T> page(List<T> content, Pageable pageable, long total) {
        int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) total / pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }

    private long count(String jpql, Map<String, Object> params) {
        var query = em.createQuery(jpql);
        params.forEach(query::setParameter);
        return toLong(query.getSingleResult());
    }

    private BigDecimal sum(String jpql, Map<String, Object> params) {
        var query = em.createQuery(jpql);
        params.forEach(query::setParameter);
        return bd(query.getSingleResult());
    }

    private BigDecimal paidGmv(Instant from, Instant to) {
        return sum("SELECT COALESCE(SUM(o.finalAmount),0) FROM Order o WHERE o.paidAt >= :from AND o.paidAt < :to", Map.of("from", from, "to", to));
    }

    private FunnelStep step(String key, String label, long count, long previous, long first) {
        return new FunnelStep(key, label, count, percent(count, previous), percent(count, first));
    }

    private ZoneId zone(String timezone) {
        if (!text(timezone)) return DEFAULT_ZONE;
        try { return ZoneId.of(timezone); } catch (DateTimeException ex) { return DEFAULT_ZONE; }
    }

    private Instant start(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    private Double percent(long part, long total) {
        if (total <= 0) return null;
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private Double delta(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 4, RoundingMode.HALF_UP).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal bd(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private String like(String keyword) {
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private boolean text(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String primary, String fallback) {
        return text(primary) ? primary : fallback;
    }

    private String money(BigDecimal value) {
        return nvl(value).setScale(0, RoundingMode.HALF_UP).toPlainString() + " VND";
    }
}
