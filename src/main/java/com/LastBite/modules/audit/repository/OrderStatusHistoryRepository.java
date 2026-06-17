package com.LastBite.modules.audit.repository;

import com.LastBite.modules.audit.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {

    @Query("""
        SELECT h FROM OrderStatusHistory h
        LEFT JOIN FETCH h.actor
        WHERE h.order.id = :orderId
        ORDER BY h.createdAt ASC
    """)
    List<OrderStatusHistory> findTimelineByOrderId(@Param("orderId") UUID orderId);
}
