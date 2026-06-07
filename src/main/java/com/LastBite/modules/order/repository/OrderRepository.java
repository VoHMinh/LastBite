package com.LastBite.modules.order.repository;

import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
