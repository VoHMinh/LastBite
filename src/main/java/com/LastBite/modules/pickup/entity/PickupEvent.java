package com.LastBite.modules.pickup.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.pickup.enums.PickupChannel;
import com.LastBite.modules.pickup.enums.PickupEventType;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "pickup_events", indexes = {
        @Index(name = "idx_pickup_events_order_id", columnList = "order_id"),
        @Index(name = "idx_pickup_events_store_created", columnList = "store_id,created_at")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PickupEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private PickupEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PickupChannel channel;

    @Column(length = 1000)
    private String notes;
}
