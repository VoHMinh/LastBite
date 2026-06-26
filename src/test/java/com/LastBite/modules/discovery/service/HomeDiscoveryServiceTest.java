package com.LastBite.modules.discovery.service;

import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.BagDiscoveryProjection;
import com.LastBite.modules.bag.service.impl.BagDiscoveryMapper;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.discovery.entity.DiscoveryCollection;
import com.LastBite.modules.discovery.enums.DiscoveryCollectionType;
import com.LastBite.modules.discovery.enums.DiscoveryRule;
import com.LastBite.modules.discovery.enums.DiscoverySort;
import com.LastBite.modules.discovery.repository.DiscoveryCollectionItemRepository;
import com.LastBite.modules.discovery.repository.DiscoveryCollectionRepository;
import com.LastBite.modules.discovery.repository.PlatformConfigRepository;
import com.LastBite.modules.media.service.MediaUrlService;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeDiscoveryServiceTest {

    private final DiscoveryCollectionRepository collectionRepository = mock(DiscoveryCollectionRepository.class);
    private final DiscoveryCollectionItemRepository itemRepository = mock(DiscoveryCollectionItemRepository.class);
    private final BagDailyStockRepository stockRepository = mock(BagDailyStockRepository.class);
    private final UserDiscoveryPreferenceRepository preferenceRepository = mock(UserDiscoveryPreferenceRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final FavoriteStoreRepository favoriteStoreRepository = mock(FavoriteStoreRepository.class);
    private final PlatformConfigRepository platformConfigRepository = mock(PlatformConfigRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BagPricingService pricingService = new BagPricingService(clock);
    private final MediaUrlService mediaUrlService = mock(MediaUrlService.class);
    private final BagDiscoveryMapper mapper = new BagDiscoveryMapper(
            favoriteStoreRepository, pricingService, mediaUrlService, clock);
    private final DiscoveryRankingService rankingService = new DiscoveryRankingService(platformConfigRepository, clock);
    private final HomeDiscoveryService service = new HomeDiscoveryService(collectionRepository, itemRepository,
            stockRepository, preferenceRepository, orderRepository, mapper, rankingService, clock);

    @Test
    void hidesCollectionWhenBelowMinimumItems() {
        DiscoveryCollection collection = collection("big_discount", DiscoveryRule.BIG_DISCOUNT,
                DiscoverySort.DISCOUNT_DESC, Map.of("minDiscountPercent", 35), 2);
        BagDiscoveryProjection discounted = row(40, Instant.parse("2026-05-20T00:00:00Z"), 0);
        when(collectionRepository.findByActiveTrueOrderByDisplayOrderAscSlugAsc()).thenReturn(List.of(collection));
        when(stockRepository.findDiscoveryCandidatesWithoutLocation(any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(discounted));

        var result = service.discover(null, null, null, 5.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void newStoresSortsByStoreCreatedAtAndBypassesRanking() {
        DiscoveryCollection collection = collection("new_stores", DiscoveryRule.NEW_STORES,
                DiscoverySort.STORE_CREATED_DESC, Map.of("days", 30), 1);
        BagDiscoveryProjection older = row(10, Instant.parse("2026-05-05T00:00:00Z"), 0);
        BagDiscoveryProjection newer = row(10, Instant.parse("2026-05-24T00:00:00Z"), 0);
        when(collectionRepository.findByActiveTrueOrderByDisplayOrderAscSlugAsc()).thenReturn(List.of(collection));
        when(stockRepository.findDiscoveryCandidatesWithoutLocation(any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(older, newer));

        var result = service.discover(null, null, null, 5.0);

        assertEquals(1, result.size());
        assertEquals(newer.getBagId(), result.getFirst().getItems().getFirst().getBagId());
    }

    @Test
    void recommendedForYouIsHiddenForAnonymousUsers() {
        DiscoveryCollection collection = DiscoveryCollection.builder()
                .slug("recommended_for_you")
                .title("Recommended")
                .type(DiscoveryCollectionType.PERSONALIZED)
                .ruleDefinition(rule(DiscoveryRule.RECOMMENDED_FOR_YOU, DiscoverySort.PERSONALIZED_DESC, Map.of()))
                .displayOrder(1)
                .maxItems(10)
                .minItemsToDisplay(1)
                .active(true)
                .build();
        BagDiscoveryProjection candidate = row(10, Instant.parse("2026-05-20T00:00:00Z"), 0);
        when(collectionRepository.findByActiveTrueOrderByDisplayOrderAscSlugAsc()).thenReturn(List.of(collection));
        when(stockRepository.findDiscoveryCandidatesWithoutLocation(any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(candidate));

        var result = service.discover(null, null, null, 5.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void bestsellerTodayUsesOrdersTodayCount() {
        DiscoveryCollection collection = collection("bestseller_today", DiscoveryRule.BESTSELLER_TODAY,
                DiscoverySort.ORDERS_TODAY_DESC, Map.of(), 1);
        BagDiscoveryProjection unsold = row(10, Instant.parse("2026-05-20T00:00:00Z"), 0);
        BagDiscoveryProjection bestseller = row(10, Instant.parse("2026-05-20T00:00:00Z"), 5);
        when(collectionRepository.findByActiveTrueOrderByDisplayOrderAscSlugAsc()).thenReturn(List.of(collection));
        when(stockRepository.findDiscoveryCandidatesWithoutLocation(any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(unsold, bestseller));

        var result = service.discover(null, null, null, 5.0);

        assertEquals(1, result.size());
        assertEquals(bestseller.getBagId(), result.getFirst().getItems().getFirst().getBagId());
    }

    @Test
    void migrationSeedsAllEightMvpCollections() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V17__discovery_collections_ranking_engine.sql"));

        for (String slug : List.of("near_you", "last_chance", "big_discount", "under_30k",
                "new_stores", "top_rated", "bestseller_today", "recommended_for_you")) {
            assertTrue(migration.contains("'" + slug + "'"));
        }
    }

    private DiscoveryCollection collection(String slug, DiscoveryRule rule, DiscoverySort sort,
                                           Map<String, Object> params, int minItems) {
        return DiscoveryCollection.builder()
                .slug(slug)
                .title(slug)
                .type(DiscoveryCollectionType.RULE_BASED)
                .ruleDefinition(rule(rule, sort, params))
                .displayOrder(1)
                .maxItems(10)
                .minItemsToDisplay(minItems)
                .active(true)
                .build();
    }

    private Map<String, Object> rule(DiscoveryRule rule, DiscoverySort sort, Map<String, Object> params) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("rule", rule.name());
        value.put("sort", sort.name());
        value.put("params", params);
        return value;
    }

    private BagDiscoveryProjection row(int discountPercent, Instant storeCreatedAt, int ordersTodayCount) {
        BagDiscoveryProjection row = mock(BagDiscoveryProjection.class);
        when(row.getBagId()).thenReturn(UUID.randomUUID());
        when(row.getStoreId()).thenReturn(UUID.randomUUID());
        when(row.getStoreName()).thenReturn("Cafe Test");
        when(row.getStoreSlug()).thenReturn("cafe-test");
        when(row.getStoreAddress()).thenReturn("Quan 1");
        when(row.getStoreAvgRating()).thenReturn(4.7);
        when(row.getStoreTotalRatings()).thenReturn(16);
        when(row.getDistrict()).thenReturn("Quan 1");
        when(row.getCity()).thenReturn("ho-chi-minh");
        when(row.getName()).thenReturn("Meal bag");
        when(row.getDescription()).thenReturn("Surprise meal");
        when(row.getBagType()).thenReturn(BagType.MEAL.name());
        when(row.getDietType()).thenReturn(DietType.MEAT.name());
        when(row.getCategory()).thenReturn(StoreCategory.CAFE.name());
        when(row.getBagSize()).thenReturn(BagSize.STANDARD.name());
        when(row.getMinimumValue()).thenReturn(BigDecimal.valueOf(100000));
        when(row.getBaseSalePrice()).thenReturn(BigDecimal.valueOf(100000 - discountPercent * 1000L));
        when(row.getDynamicMinPrice()).thenReturn(BigDecimal.valueOf(100000 - discountPercent * 1000L));
        when(row.getDynamicMaxPrice()).thenReturn(BigDecimal.valueOf(100000 - discountPercent * 1000L));
        when(row.getDynamicPricingEnabled()).thenReturn(false);
        when(row.getPlatformFee()).thenReturn(BigDecimal.valueOf(4000));
        when(row.getMaxPerOrder()).thenReturn(1);
        when(row.getContainerProvided()).thenReturn(true);
        when(row.getCarrierBagProvided()).thenReturn(true);
        when(row.getStockDate()).thenReturn(LocalDate.of(2026, 5, 25));
        when(row.getPickupStartTime()).thenReturn(LocalTime.of(20, 0));
        when(row.getPickupEndTime()).thenReturn(LocalTime.of(21, 0));
        when(row.getQuantity()).thenReturn(3);
        when(row.getReserved()).thenReturn(0);
        when(row.getSold()).thenReturn(0);
        when(row.getAvailable()).thenReturn(3);
        when(row.getStoreCreatedAt()).thenReturn(storeCreatedAt);
        when(row.getOrdersTodayCount()).thenReturn(ordersTodayCount);
        return row;
    }
}
