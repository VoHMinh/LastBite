package com.LastBite.modules.audit.service;

import com.LastBite.modules.audit.entity.OrderStatusHistory;
import com.LastBite.modules.audit.enums.AuditActorType;
import com.LastBite.modules.audit.repository.OrderStatusHistoryRepository;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository repository;

    public void record(Order order, OrderStatus fromStatus, OrderStatus toStatus,
                       User actor, AuditActorType actorType, String reason, String metadata) {
        repository.save(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actor(actor)
                .actorType(actorType == null ? AuditActorType.SYSTEM : actorType)
                .reason(reason)
                .metadata(metadata)
                .build());
    }
}
