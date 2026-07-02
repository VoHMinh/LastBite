package com.LastBite.modules.user.service;

import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.user.dto.response.UserImpactResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImpactCalculationService {

    public static final BigDecimal KG_CO2E_PER_KG_FOOD = new BigDecimal("2.7");
    public static final BigDecimal KG_PER_MEAL = new BigDecimal("0.420");

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public UserImpactResponse calculate(UUID userId) {
        List<Order> orders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PICKED_UP);

        BigDecimal totalKg = BigDecimal.ZERO;
        int totalMeals = 0;

        for (Order order : orders) {
            totalMeals += order.getQuantity();
            BigDecimal kg = lookupWeightKg(order.getBag().getBagType(), order.getBag().getBagSize());
            totalKg = totalKg.add(kg.multiply(BigDecimal.valueOf(order.getQuantity())));
        }

        BigDecimal totalCo2Kg = totalKg.multiply(KG_CO2E_PER_KG_FOOD)
                .setScale(2, RoundingMode.HALF_UP);

        return UserImpactResponse.builder()
                .totalCo2Kg(totalCo2Kg)
                .totalMeals(totalMeals)
                .totalBagsCollected(orders.size())
                .build();
    }

    private BigDecimal lookupWeightKg(BagType bagType, BagSize bagSize) {
        Map<String, BigDecimal> weightMap = buildWeightMap();
        String key = bagType.name() + "_" + bagSize.name();
        return weightMap.getOrDefault(key, KG_PER_MEAL);
    }

    private Map<String, BigDecimal> buildWeightMap() {
        Map<String, BigDecimal> m = new HashMap<>();

        m.put("MEAL_MINI",     new BigDecimal("0.5"));
        m.put("MEAL_SMALL",    new BigDecimal("0.8"));
        m.put("MEAL_STANDARD", new BigDecimal("1.0"));
        m.put("MEAL_LARGE",    new BigDecimal("1.5"));

        m.put("BREAD_MINI",     new BigDecimal("0.3"));
        m.put("BREAD_SMALL",    new BigDecimal("0.5"));
        m.put("BREAD_STANDARD", new BigDecimal("0.7"));
        m.put("BREAD_LARGE",    new BigDecimal("1.0"));

        m.put("GROCERY_MINI",     new BigDecimal("0.5"));
        m.put("GROCERY_SMALL",    new BigDecimal("1.0"));
        m.put("GROCERY_STANDARD", new BigDecimal("1.5"));
        m.put("GROCERY_LARGE",    new BigDecimal("2.5"));

        m.put("STANDARD_MINI",     new BigDecimal("0.3"));
        m.put("STANDARD_SMALL",    new BigDecimal("0.5"));
        m.put("STANDARD_STANDARD", new BigDecimal("0.8"));
        m.put("STANDARD_LARGE",    new BigDecimal("1.2"));

        m.put("MIXED_MINI",     new BigDecimal("0.4"));
        m.put("MIXED_SMALL",    new BigDecimal("0.6"));
        m.put("MIXED_STANDARD", new BigDecimal("1.0"));
        m.put("MIXED_LARGE",    new BigDecimal("1.5"));

        return m;
    }
}
