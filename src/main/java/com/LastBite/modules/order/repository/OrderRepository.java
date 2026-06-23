package com.LastBite.modules.order.repository;

import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.store.enums.StoreCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUser_IdAndIdempotencyKey(UUID userId, String idempotencyKey);

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
          AND (:pickupDateFrom IS NULL OR o.pickupDate >= :pickupDateFrom)
          AND (:pickupDateTo IS NULL OR o.pickupDate <= :pickupDateTo)
    """)
    Page<Order> searchCustomerOrders(@Param("userId") UUID userId,
                                     @Param("status") OrderStatus status,
                                     @Param("refundStatus") OrderRefundStatus refundStatus,
                                     @Param("pickupDateFrom") LocalDate pickupDateFrom,
                                     @Param("pickupDateTo") LocalDate pickupDateTo,
                                     Pageable pageable);

    @EntityGraph(attributePaths = {"user", "store", "bag", "dailyStock"})
    @Query("""
        SELECT o FROM Order o
        WHERE o.store.id = :storeId
          AND (:pickupDate IS NULL OR o.pickupDate = :pickupDate)
          AND (:status IS NULL OR o.status = :status)
    """)
    Page<Order> searchStoreOrders(@Param("storeId") UUID storeId,
                                  @Param("pickupDate") LocalDate pickupDate,
                                  @Param("status") OrderStatus status,
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
    """)
    List<Order> findOrdersPastPickupWindow(@Param("statuses") List<OrderStatus> statuses,
                                           @Param("today") LocalDate today,
                                           @Param("cutoffTime") LocalTime cutoffTime);
    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.store.id = :storeId
          AND o.paidAt IS NOT NULL
          AND o.paidAt >= :from
    """)
    long countPaidOrdersSince(@Param("storeId") UUID storeId, @Param("from") Instant from);

    long countByUser_IdAndPaidAtIsNotNull(UUID userId);

    long countByUser_IdAndStatus(UUID userId, OrderStatus status);
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
