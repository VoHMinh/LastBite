package com.LastBite.modules.order.repository;

import com.LastBite.modules.analytics.repository.OrderAnalyticsProjection;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.store.enums.StoreCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByUser_IdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Query("SELECT o FROM Order o JOIN FETCH o.bag WHERE o.user.id = :userId AND o.status = :status")
    List<Order> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.user
        JOIN FETCH o.store
        JOIN FETCH o.bag
        JOIN FETCH o.dailyStock
        WHERE o.id = :orderId
    """)
    Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);

    @Query("SELECT o FROM Order o WHERE o.id = :orderId AND o.user.id = :userId")
    Optional<Order> findByIdAndUserId(@Param("orderId") UUID orderId, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"user", "store", "bag", "dailyStock"})
    @Query("""
        SELECT o FROM Order o
        WHERE o.user.id = :userId
          AND (:status IS NULL OR o.status = :status)
          AND (:refundStatus IS NULL OR o.refundStatus = :refundStatus)
          AND (COALESCE(:pickupDateFrom, o.pickupDate) <= o.pickupDate)
          AND (COALESCE(:pickupDateTo, o.pickupDate) >= o.pickupDate)
    """)
    Page<Order> searchCustomerOrders(@Param("userId") UUID userId,
                                     @Param("status") OrderStatus status,
                                     @Param("refundStatus") OrderRefundStatus refundStatus,
                                     @Param("pickupDateFrom") LocalDate pickupDateFrom,
                                     @Param("pickupDateTo") LocalDate pickupDateTo,
                                     Pageable pageable);

    @Query(value = """
        SELECT o FROM Order o
        JOIN FETCH o.user
        JOIN FETCH o.store
        JOIN FETCH o.bag
        JOIN FETCH o.dailyStock
        WHERE o.store.id = :storeId
          AND o.pickupDate = :pickupDate
        """,
        countProjection = "COUNT(o.id)")
    Page<Order> searchStoreOrders(@Param("storeId") UUID storeId,
                                  @Param("pickupDate") LocalDate pickupDate,
                                  Pageable pageable);

    @EntityGraph(attributePaths = {"user", "store", "bag", "dailyStock"})
    @Query("SELECT o FROM Order o WHERE o.id = :orderId AND o.store.id = :storeId")
    Optional<Order> findByIdAndStoreId(@Param("orderId") UUID orderId, @Param("storeId") UUID storeId);

    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.user
        JOIN FETCH o.store
        JOIN FETCH o.bag
        WHERE o.status = com.LastBite.modules.order.enums.OrderStatus.PENDING_PAYMENT
          AND o.reservedUntil > :now
          AND o.reservedUntil <= :threshold
    """)
    List<Order> findPendingPaymentsExpiring(@Param("now") Instant now, @Param("threshold") Instant threshold);

    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.user
        JOIN FETCH o.store
        JOIN FETCH o.bag
        WHERE o.status IN :statuses
          AND o.pickupDate = :pickupDate
          AND o.pickupStartTime > :nowTime
          AND o.pickupStartTime <= :thresholdTime
    """)
    List<Order> findPickupStartingSoon(@Param("statuses") List<OrderStatus> statuses,
                                       @Param("pickupDate") LocalDate pickupDate,
                                       @Param("nowTime") LocalTime nowTime,
                                       @Param("thresholdTime") LocalTime thresholdTime);

    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.user
        JOIN FETCH o.store
        JOIN FETCH o.bag
        WHERE o.status IN :statuses
          AND o.pickupDate = :pickupDate
          AND o.pickupStartTime <= :nowTime
          AND o.pickupEndTime > :nowTime
    """)
    List<Order> findPickupWindowOpen(@Param("statuses") List<OrderStatus> statuses,
                                     @Param("pickupDate") LocalDate pickupDate,
                                     @Param("nowTime") LocalTime nowTime);

    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.user
        JOIN FETCH o.store
        JOIN FETCH o.bag
        WHERE o.status IN :statuses
          AND o.pickupDate = :pickupDate
          AND o.pickupEndTime > :nowTime
          AND o.pickupEndTime <= :thresholdTime
    """)
    List<Order> findPickupEndingSoon(@Param("statuses") List<OrderStatus> statuses,
                                     @Param("pickupDate") LocalDate pickupDate,
                                     @Param("nowTime") LocalTime nowTime,
                                     @Param("thresholdTime") LocalTime thresholdTime);

    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.user
        JOIN FETCH o.store
        JOIN FETCH o.bag
        WHERE o.status IN :statuses
          AND (o.pickupDate < :today OR (o.pickupDate = :today AND o.pickupEndTime <= :cutoffTime))
        ORDER BY o.pickupDate ASC, o.pickupEndTime ASC, o.createdAt ASC
    """)
    List<Order> findOrdersPastPickupWindow(@Param("statuses") List<OrderStatus> statuses,
                                           @Param("today") LocalDate today,
                                           @Param("cutoffTime") LocalTime cutoffTime,
                                           Pageable pageable);

    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.store.id = :storeId
          AND o.paidAt IS NOT NULL
          AND o.paidAt >= :from
    """)
    long countPaidOrdersSince(@Param("storeId") UUID storeId, @Param("from") Instant from);

    long countByUser_IdAndPaidAtIsNotNull(UUID userId);

    long countByUser_IdAndStatus(UUID userId, OrderStatus status);

    long countByUser_IdAndStatusIn(UUID userId, List<OrderStatus> statuses);

    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.store.businessProfile.owner.id = :ownerId
          AND o.status IN :statuses
    """)
    long countByOwnerIdAndStatusIn(@Param("ownerId") UUID ownerId,
                                   @Param("statuses") List<OrderStatus> statuses);

    @Query(value = """
        SELECT
            COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to) AS "totalOrders",
            COUNT(*) FILTER (WHERE paid_at >= :from AND paid_at < :to) AS "paidOrders",
            COALESCE(SUM(quantity) FILTER (WHERE paid_at >= :from AND paid_at < :to), 0) AS "bagsSold",
            COALESCE(SUM(final_amount) FILTER (WHERE paid_at >= :from AND paid_at < :to), 0) AS "grossRevenue"
        FROM orders
        WHERE store_id = :storeId
          AND (
              (created_at >= :from AND created_at < :to)
              OR (paid_at >= :from AND paid_at < :to)
          )
    """, nativeQuery = true)
    OrderAnalyticsProjection summarizeStoreOrders(@Param("storeId") UUID storeId,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to);

    @Query("""
        SELECT o.bag.category FROM Order o
        WHERE o.user.id = :userId
          AND o.paidAt IS NOT NULL
          AND o.status NOT IN (com.LastBite.modules.order.enums.OrderStatus.CANCELLED,
                               com.LastBite.modules.order.enums.OrderStatus.REFUNDED)
        GROUP BY o.bag.category
        ORDER BY COUNT(o.id) DESC
    """)
    List<StoreCategory> findPurchasedCategoriesByUser(@Param("userId") UUID userId);
}
