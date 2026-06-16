package com.LastBite.modules.audit.repository;

import com.LastBite.modules.audit.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
}
