package com.LastBite.modules.bag.service;

import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.BagDiscoveryProjection;
import com.LastBite.modules.bag.service.impl.BagDiscoveryService;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BagDiscoveryServiceTest {

    private final BagDailyStockRepository stockRepository = mock(BagDailyStockRepository.class);
    private final FavoriteStoreRepository favoriteStoreRepository = mock(FavoriteStoreRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BagDiscoveryService service = new BagDiscoveryService(
            stockRepository, favoriteStoreRepository, new BagPricingService(clock), clock);

    @Test
    void discoverPassesDietAndBagTypeFiltersToRepository() {
        BagDiscoveryProjection projection = row(DietType.VEGETARIAN, BagType.MEAL);
        when(stockRepository.discoverWithoutLocation(
                any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(projection));

        var results = service.discover(null, null, null, 5.0, StoreCategory.CAFE,
                DietType.VEGETARIAN, BagType.MEAL, "Quan 1", "pickup_time", 10);

        assertEquals(DietType.VEGETARIAN, results.getFirst().getDietType());
        assertEquals(BagType.MEAL, results.getFirst().getBagType());
        assertEquals("logo.png", results.getFirst().getStoreLogoUrl());
        assertEquals("cover.png", results.getFirst().getStoreCoverImageUrl());
        assertEquals(4.7, results.getFirst().getStoreAvgRating());
        assertEquals(16, results.getFirst().getStoreTotalRatings());
        assertTrue(results.getFirst().isContainerProvided());
        assertTrue(results.getFirst().isCarrierBagProvided());
        assertEquals("Bring your own bag if possible.", results.getFirst().getPackagingNote());
        assertFalse(results.getFirst().isFavoriteStore());
        verify(stockRepository).discoverWithoutLocation(
                LocalDate.of(2026, 5, 25),
                LocalTime.of(10, 0),
                "CAFE",
                "VEGETARIAN",
                "MEAL",
                "Quan 1",
                "pickup_time",
                10);
    }

    @Test
    void detailMarksFavoriteStoreForAuthenticatedUser() {
        UUID bagId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BagDiscoveryProjection projection = row(DietType.VEGETARIAN, BagType.MEAL);
        when(stockRepository.findPublicBagDetail(bagId, LocalDate.of(2026, 5, 25), LocalTime.of(10, 0)))
                .thenReturn(Optional.of(projection));
        when(favoriteStoreRepository.existsByUserIdAndStoreId(userId, projection.getStoreId())).thenReturn(true);

        var detail = service.detail(bagId, userId);

        assertTrue(detail.isFavoriteStore());
        assertEquals("logo.png", detail.getStoreLogoUrl());
        assertEquals("cover.png", detail.getStoreCoverImageUrl());
        assertTrue(detail.isContainerProvided());
        assertTrue(detail.isCarrierBagProvided());
    }

    private BagDiscoveryProjection row(DietType dietType, BagType bagType) {
        BagDiscoveryProjection row = mock(BagDiscoveryProjection.class);
        when(row.getBagId()).thenReturn(UUID.randomUUID());
        when(row.getStoreId()).thenReturn(UUID.randomUUID());
        when(row.getStoreName()).thenReturn("Cafe Test");
        when(row.getStoreSlug()).thenReturn("cafe-test");
        when(row.getStoreAddress()).thenReturn("Quan 1");
        when(row.getStoreLogoUrl()).thenReturn("logo.png");
        when(row.getStoreCoverImageUrl()).thenReturn("cover.png");
        when(row.getStoreAvgRating()).thenReturn(4.7);
        when(row.getStoreTotalRatings()).thenReturn(16);
        when(row.getDistrict()).thenReturn("Quan 1");
        when(row.getCity()).thenReturn("ho-chi-minh");
        when(row.getName()).thenReturn("Meal bag");
        when(row.getDescription()).thenReturn("Surprise meal");
        when(row.getBagType()).thenReturn(bagType.name());
        when(row.getDietType()).thenReturn(dietType.name());
        when(row.getCategory()).thenReturn(StoreCategory.CAFE.name());
        when(row.getBagSize()).thenReturn(BagSize.STANDARD.name());
        when(row.getMinimumValue()).thenReturn(BigDecimal.valueOf(100000));
        when(row.getBaseSalePrice()).thenReturn(BigDecimal.valueOf(39000));
        when(row.getDynamicMinPrice()).thenReturn(BigDecimal.valueOf(35000));
        when(row.getDynamicMaxPrice()).thenReturn(BigDecimal.valueOf(45000));
        when(row.getDynamicPricingEnabled()).thenReturn(true);
        when(row.getPlatformFee()).thenReturn(BigDecimal.valueOf(4000));
        when(row.getMaxPerOrder()).thenReturn(1);
        when(row.getContainerProvided()).thenReturn(true);
        when(row.getCarrierBagProvided()).thenReturn(true);
        when(row.getPackagingNote()).thenReturn("Bring your own bag if possible.");
        when(row.getStockDate()).thenReturn(LocalDate.of(2026, 5, 25));
        when(row.getPickupStartTime()).thenReturn(LocalTime.of(20, 0));
        when(row.getPickupEndTime()).thenReturn(LocalTime.of(21, 0));
        when(row.getQuantity()).thenReturn(3);
        when(row.getReserved()).thenReturn(0);
        when(row.getSold()).thenReturn(0);
        when(row.getAvailable()).thenReturn(3);
        return row;
    }
}
