package com.LastBite.modules.bag.service;

import com.LastBite.modules.bag.service.impl.BagPricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BagPricingServiceTest {

    private static final LocalDate STOCK_DATE = LocalDate.of(2026, 5, 25);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void dynamicPriceStartsAtHighestConfiguredPrice() {
        BagPricingService service = serviceAt("2026-05-24T17:00:00Z");

        var price = service.currentPrice(
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(39000),
                BigDecimal.valueOf(35000),
                BigDecimal.valueOf(45000),
                true,
                STOCK_DATE,
                LocalTime.of(21, 0));

        assertEquals(BigDecimal.valueOf(45000), price.currentSalePrice());
        assertEquals(55, price.currentDiscountPercent());
    }

    @Test
    void dynamicPriceDropsTowardPickupEnd() {
        BagPricingService earlyService = serviceAt("2026-05-25T03:00:00Z");
        BagPricingService lateService = serviceAt("2026-05-25T13:00:00Z");

        var earlyPrice = earlyService.currentPrice(
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(39000),
                BigDecimal.valueOf(35000),
                BigDecimal.valueOf(45000),
                true,
                STOCK_DATE,
                LocalTime.of(21, 0));
        var latePrice = lateService.currentPrice(
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(39000),
                BigDecimal.valueOf(35000),
                BigDecimal.valueOf(45000),
                true,
                STOCK_DATE,
                LocalTime.of(21, 0));

        assertTrue(latePrice.currentSalePrice().compareTo(earlyPrice.currentSalePrice()) < 0);
        assertTrue(latePrice.currentDiscountPercent() > earlyPrice.currentDiscountPercent());
    }

    @Test
    void disabledDynamicPricingUsesBaseSalePrice() {
        BagPricingService service = serviceAt("2026-05-25T13:00:00Z");

        var price = service.currentPrice(
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(39000),
                BigDecimal.valueOf(35000),
                BigDecimal.valueOf(45000),
                false,
                STOCK_DATE,
                LocalTime.of(21, 0));

        assertEquals(BigDecimal.valueOf(39000), price.currentSalePrice());
        assertEquals(61, price.currentDiscountPercent());
    }

    private BagPricingService serviceAt(String instant) {
        return new BagPricingService(Clock.fixed(Instant.parse(instant), ZONE));
    }
}
