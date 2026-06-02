package com.LastBite.modules.bag.service.impl;

import com.LastBite.modules.bag.entity.SurpriseBag;
import lombok.Builder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class BagPricingService {

    private static final int PRICE_LEVELS = 5;

    private final Clock clock;

    public BagPricingService(Clock clock) {
        this.clock = clock;
    }

    public PriceSnapshot currentPrice(SurpriseBag bag, LocalDate stockDate) {
        return currentPrice(
                bag.getMinimumValue(),
                bag.getBaseSalePrice(),
                bag.getDynamicMinPrice(),
                bag.getDynamicMaxPrice(),
                bag.isDynamicPricingEnabled(),
                stockDate,
                bag.getPickupEndTime());
    }

    public PriceSnapshot currentPrice(BigDecimal minimumValue, BigDecimal baseSalePrice,
                                      BigDecimal dynamicMinPrice, BigDecimal dynamicMaxPrice,
                                      boolean dynamicPricingEnabled,
                                      LocalDate stockDate, LocalTime pickupEndTime) {
        BigDecimal price = dynamicPricingEnabled
                ? dynamicPrice(dynamicMinPrice, dynamicMaxPrice, stockDate, pickupEndTime)
                : baseSalePrice;
        BigDecimal savings = minimumValue.subtract(price).max(BigDecimal.ZERO);
        int discountPercent = savings.multiply(BigDecimal.valueOf(100))
                .divide(minimumValue, 0, RoundingMode.HALF_UP)
                .intValue();
        return PriceSnapshot.builder()
                .currentSalePrice(price)
                .savingsAmount(savings)
                .currentDiscountPercent(discountPercent)
                .build();
    }

    private BigDecimal dynamicPrice(BigDecimal minPrice, BigDecimal maxPrice,
                                    LocalDate stockDate, LocalTime pickupEndTime) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime salesStart = stockDate.atStartOfDay();
        LocalDateTime salesEnd = stockDate.atTime(pickupEndTime);

        if (!now.isAfter(salesStart)) return maxPrice;
        if (!now.isBefore(salesEnd)) return minPrice;

        long totalSeconds = java.time.Duration.between(salesStart, salesEnd).toSeconds();
        long elapsedSeconds = java.time.Duration.between(salesStart, now).toSeconds();
        int level = (int) Math.min(PRICE_LEVELS - 1,
                Math.floor((double) elapsedSeconds / Math.max(1, totalSeconds) * PRICE_LEVELS));

        BigDecimal step = maxPrice.subtract(minPrice)
                .divide(BigDecimal.valueOf(PRICE_LEVELS - 1L), 0, RoundingMode.HALF_UP);
        BigDecimal price = maxPrice.subtract(step.multiply(BigDecimal.valueOf(level)));
        return roundToNearestThousand(price).max(minPrice).min(maxPrice);
    }

    private BigDecimal roundToNearestThousand(BigDecimal value) {
        return value.divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(1000));
    }

    @Builder
    public record PriceSnapshot(
            BigDecimal currentSalePrice,
            BigDecimal savingsAmount,
            int currentDiscountPercent
    ) {
    }
}
