package com.LastBite.modules.order.specification;

import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> forStore(UUID storeId) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user");
                root.fetch("store");
                root.fetch("bag");
                root.fetch("dailyStock");
            }
            return cb.equal(root.get("store").get("id"), storeId);
        };
    }

    public static Specification<Order> withPickupDate(LocalDate date) {
        return (root, query, cb) -> {
            if (date == null) return cb.conjunction();
            return cb.equal(root.get("pickupDate"), date);
        };
    }

    public static Specification<Order> withStatus(OrderStatus status) {
        return (root, query, cb) -> {
            if (status == null) return cb.conjunction();
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Order> storeOrders(UUID storeId, LocalDate pickupDate, OrderStatus status) {
        return Specification.where(forStore(storeId))
                .and(withPickupDate(pickupDate))
                .and(withStatus(status));
    }
}
