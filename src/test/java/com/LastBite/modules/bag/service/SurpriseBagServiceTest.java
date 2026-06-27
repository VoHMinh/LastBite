package com.LastBite.modules.bag.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.dto.request.CreateSurpriseBagRequest;
import com.LastBite.modules.bag.dto.request.SetDailyStockRequest;
import com.LastBite.modules.bag.dto.request.WeeklyStockPlanItemRequest;
import com.LastBite.modules.bag.dto.response.StockCalendarResponse;
import com.LastBite.modules.bag.dto.response.SurpriseBagResponse;
import com.LastBite.modules.bag.entity.BagPriceTier;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagStatus;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DailyStockSource;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.BagPriceTierRepository;
import com.LastBite.modules.bag.repository.StockAuditLogRepository;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.bag.service.impl.SurpriseBagService;
import com.LastBite.modules.media.service.MediaUrlService;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.merchant.service.StoreAccessService;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SurpriseBagServiceTest {

    private final SurpriseBagRepository bagRepository = mock(SurpriseBagRepository.class);
    private final BagDailyStockRepository stockRepository = mock(BagDailyStockRepository.class);
    private final BagPriceTierRepository priceTierRepository = mock(BagPriceTierRepository.class);
    private final StockAuditLogRepository auditLogRepository = mock(StockAuditLogRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationServicePort notificationService = mock(NotificationServicePort.class);
    private final StoreAccessService storeAccessService = mock(StoreAccessService.class);
    private final MediaUrlService mediaUrlService = mock(MediaUrlService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BagPricingService pricingService = new BagPricingService(clock);

    private SurpriseBagService service;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        service = new SurpriseBagService(bagRepository, stockRepository, priceTierRepository,
                auditLogRepository, storeRepository, userRepository, pricingService, notificationService,
                storeAccessService, mediaUrlService, clock);
        ownerId = UUID.randomUUID();
        when(mediaUrlService.resolveUrls(any(Collection.class)))
                .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));

        Store store = Store.builder()
                .name("Tiem banh Test")
                .slug("tiem-banh-test")
                .category(StoreCategory.BAKERY)
                .address("Quan 1")
                .status(StoreStatus.ACTIVE)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        ReflectionTestUtils.setField(store, "id", UUID.randomUUID());
        when(storeRepository.findFirstByBusinessProfileOwnerIdOrderByCreatedAtAsc(ownerId))
                .thenReturn(Optional.of(store));
        when(priceTierRepository.findByCategoryAndBagSizeAndActiveTrue(StoreCategory.BAKERY, BagSize.STANDARD))
                .thenReturn(Optional.of(priceTier(StoreCategory.BAKERY, BagSize.STANDARD)));
        when(bagRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createSnapshotsPriceFromPlatformTier() {
        assertDoesNotThrow(() -> service.create(ownerId, validRequest()));

        verify(bagRepository).save(argThat(argument -> {
            SurpriseBag bag = (SurpriseBag) argument;
            assertEquals(StoreCategory.BAKERY, bag.getCategory());
            assertEquals(DietType.MEAT, bag.getDietType());
            assertEquals(BagSize.STANDARD, bag.getBagSize());
            assertEquals(BigDecimal.valueOf(100000), bag.getMinimumValue());
            assertEquals(BigDecimal.valueOf(39000), bag.getBaseSalePrice());
            assertEquals(BigDecimal.valueOf(35000), bag.getDynamicMinPrice());
            assertEquals(BigDecimal.valueOf(45000), bag.getDynamicMaxPrice());
            assertEquals(true, bag.isContainerProvided());
            assertEquals(true, bag.isCarrierBagProvided());
            assertEquals("We recommend bringing your own bag.", bag.getPackagingNote());
            return true;
        }));
    }

    @Test
    void createRejectsMissingPlatformTier() {
        CreateSurpriseBagRequest request = validRequest();
        request.setBagSize(BagSize.LARGE);
        when(priceTierRepository.findByCategoryAndBagSizeAndActiveTrue(StoreCategory.BAKERY, BagSize.LARGE))
                .thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.create(ownerId, request));

        verify(bagRepository, never()).save(any());
    }

    @Test
    void createRejectsPickupWindowShorterThanThirtyMinutes() {
        CreateSurpriseBagRequest request = validRequest();
        request.setPickupStartTime(LocalTime.of(20, 0));
        request.setPickupEndTime(LocalTime.of(20, 20));

        assertThrows(ApiException.class, () -> service.create(ownerId, request));

        verify(bagRepository, never()).save(any());
    }

    @Test
    void createAcceptsValidBagRules() {
        assertDoesNotThrow(() -> service.create(ownerId, validRequest()));

        verify(bagRepository).save(any());
    }

    @Test
    void createAcceptsExplicitDietType() {
        CreateSurpriseBagRequest request = validRequest();
        request.setDietType(DietType.VEGAN);

        assertDoesNotThrow(() -> service.create(ownerId, request));

        verify(bagRepository).save(argThat(argument -> {
            SurpriseBag bag = (SurpriseBag) argument;
            assertEquals(DietType.VEGAN, bag.getDietType());
            return true;
        }));
    }

    @Test
    void createAcceptsCustomPackaging() {
        CreateSurpriseBagRequest request = validRequest();
        request.setContainerProvided(false);
        request.setCarrierBagProvided(true);
        request.setPackagingNote("Bring a clean cup.");

        SurpriseBagResponse response = service.create(ownerId, request);

        assertFalse(response.isContainerProvided());
        assertEquals(true, response.isCarrierBagProvided());
        assertEquals("Bring a clean cup.", response.getPackagingNote());
        verify(bagRepository).save(argThat(argument -> {
            SurpriseBag bag = (SurpriseBag) argument;
            return !bag.isContainerProvided()
                    && bag.isCarrierBagProvided()
                    && "Bring a clean cup.".equals(bag.getPackagingNote());
        }));
    }

    @Test
    void createSnapshotsWeeklyStockPlan() {
        CreateSurpriseBagRequest request = validRequest();
        request.setWeeklyStockPlan(List.of(weeklyPlan(1, 8), weeklyPlan(3, 4)));

        SurpriseBagResponse response = service.create(ownerId, request);

        assertEquals(8, response.getWeeklyStockPlan().get(1).getQuantity());
        assertEquals(4, response.getWeeklyStockPlan().get(3).getQuantity());
        verify(bagRepository).save(argThat(argument -> {
            SurpriseBag bag = (SurpriseBag) argument;
            return bag.getWeeklyStockPlan()[1] == 8 && bag.getWeeklyStockPlan()[3] == 4;
        }));
    }

    @Test
    void createRejectsWeeklyStockForUnavailableDay() {
        CreateSurpriseBagRequest request = validRequest();
        request.setWeeklyStockPlan(List.of(weeklyPlan(0, 5)));

        assertThrows(ApiException.class, () -> service.create(ownerId, request));

        verify(bagRepository, never()).save(any());
    }

    @Test
    void createUpcomingStocksUsesWeeklyPlanAndDoesNotCreateZeroQuantityRows() {
        SurpriseBag bag = existingBag(UUID.randomUUID());
        bag.setAvailableDays(new Integer[]{2});
        bag.setWeeklyStockPlan(new Integer[]{0, 0, 7, 0, 0, 0, 0});
        when(bagRepository.findActiveBagsMissingStockForDate(any())).thenReturn(List.of());
        when(bagRepository.findActiveBagsMissingStockForDate(LocalDate.of(2026, 5, 26)))
                .thenReturn(List.of(bag));

        int created = service.createUpcomingStocks();

        assertEquals(1, created);
        verify(stockRepository).save(argThat(argument -> {
            BagDailyStock stock = (BagDailyStock) argument;
            return stock.getDate().equals(LocalDate.of(2026, 5, 26))
                    && stock.getQuantity() == 7
                    && stock.getSource() == DailyStockSource.WEEKLY_DEFAULT;
        }));
    }

    @Test
    void setStockMarksRowAsManualOverride() {
        UUID bagId = UUID.randomUUID();
        SurpriseBag bag = existingBag(bagId);
        when(bagRepository.findByIdAndStoreBusinessProfileOwnerId(bagId, ownerId))
                .thenReturn(Optional.of(bag));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(com.LastBite.modules.auth.entity.User.builder()
                .email("owner@test.com")
                .fullName("Owner")
                .build()));
        SetDailyStockRequest request = new SetDailyStockRequest();
        request.setQuantity(6);

        service.setStock(ownerId, bagId, LocalDate.of(2026, 5, 25), request);

        verify(stockRepository).save(argThat(argument -> {
            BagDailyStock stock = (BagDailyStock) argument;
            return stock.getQuantity() == 6 && stock.getSource() == DailyStockSource.MANUAL;
        }));
    }

    @Test
    void stockCalendarCombinesManualRowsAndWeeklyDefaults() {
        UUID bagId = UUID.randomUUID();
        SurpriseBag bag = existingBag(bagId);
        bag.setWeeklyStockPlan(new Integer[]{0, 9, 5, 0, 0, 0, 0});
        when(bagRepository.findByIdAndStoreBusinessProfileOwnerId(bagId, ownerId))
                .thenReturn(Optional.of(bag));
        BagDailyStock manual = BagDailyStock.builder()
                .bag(bag)
                .store(bag.getStore())
                .date(LocalDate.of(2026, 5, 25))
                .quantity(3)
                .reserved(1)
                .sold(1)
                .status(DailyStockStatus.ACTIVE)
                .source(DailyStockSource.MANUAL)
                .build();
        when(stockRepository.findByBagIdAndDateBetweenOrderByDateAsc(
                bagId, LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 26)))
                .thenReturn(List.of(manual));

        List<StockCalendarResponse> calendar = service.stockCalendar(
                ownerId, bagId, LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 26));

        assertEquals(DailyStockSource.MANUAL, calendar.get(0).getSource());
        assertEquals(1, calendar.get(0).getAvailable());
        assertEquals(DailyStockSource.WEEKLY_DEFAULT, calendar.get(1).getSource());
        assertEquals(5, calendar.get(1).getQuantity());
    }

    @Test
    void updateChangesDietTypeWhenProvided() {
        UUID bagId = UUID.randomUUID();
        SurpriseBag bag = existingBag(bagId);
        when(bagRepository.findByIdAndStoreBusinessProfileOwnerId(bagId, ownerId))
                .thenReturn(Optional.of(bag));

        var request = new com.LastBite.modules.bag.dto.request.UpdateSurpriseBagRequest();
        request.setDietType(DietType.VEGETARIAN);
        request.setContainerProvided(false);
        request.setCarrierBagProvided(false);
        request.setPackagingNote("No bag provided today.");

        SurpriseBagResponse response = service.update(ownerId, bagId, request);

        assertEquals(DietType.VEGETARIAN, response.getDietType());
        assertFalse(response.isContainerProvided());
        assertFalse(response.isCarrierBagProvided());
        assertEquals("No bag provided today.", response.getPackagingNote());
        verify(bagRepository).save(argThat(argument -> ((SurpriseBag) argument).getDietType() == DietType.VEGETARIAN));
    }

    private CreateSurpriseBagRequest validRequest() {
        CreateSurpriseBagRequest request = new CreateSurpriseBagRequest();
        request.setName("Tui banh cuoi ngay");
        request.setBagType(BagType.BREAD);
        request.setCategory(StoreCategory.BAKERY);
        request.setBagSize(BagSize.STANDARD);
        request.setPickupStartTime(LocalTime.of(20, 0));
        request.setPickupEndTime(LocalTime.of(21, 0));
        request.setAvailableDays(Set.of(1, 2, 3, 4, 5));
        return request;
    }

    private WeeklyStockPlanItemRequest weeklyPlan(int day, int quantity) {
        WeeklyStockPlanItemRequest item = new WeeklyStockPlanItemRequest();
        item.setDayOfWeek(day);
        item.setQuantity(quantity);
        return item;
    }

    private SurpriseBag existingBag(UUID bagId) {
        Store store = Store.builder()
                .name("Tiem banh Test")
                .slug("tiem-banh-test")
                .category(StoreCategory.BAKERY)
                .address("Quan 1")
                .status(StoreStatus.ACTIVE)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        ReflectionTestUtils.setField(store, "id", UUID.randomUUID());

        SurpriseBag bag = SurpriseBag.builder()
                .store(store)
                .name("Tui banh cuoi ngay")
                .bagType(BagType.BREAD)
                .dietType(DietType.MEAT)
                .category(StoreCategory.BAKERY)
                .bagSize(BagSize.STANDARD)
                .photos(new String[0])
                .minimumValue(BigDecimal.valueOf(100000))
                .baseSalePrice(BigDecimal.valueOf(39000))
                .dynamicMinPrice(BigDecimal.valueOf(35000))
                .dynamicMaxPrice(BigDecimal.valueOf(45000))
                .dynamicPricingEnabled(true)
                .platformFee(BigDecimal.valueOf(4000))
                .maxPerOrder(1)
                .containerProvided(true)
                .carrierBagProvided(true)
                .packagingNote("We recommend bringing your own bag.")
                .pickupStartTime(LocalTime.of(20, 0))
                .pickupEndTime(LocalTime.of(21, 0))
                .availableDays(new Integer[]{1, 2, 3, 4, 5})
                .weeklyStockPlan(new Integer[]{0, 0, 0, 0, 0, 0, 0})
                .status(BagStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(bag, "id", bagId);
        return bag;
    }

    private BagPriceTier priceTier(StoreCategory category, BagSize bagSize) {
        return BagPriceTier.builder()
                .category(category)
                .bagSize(bagSize)
                .minimumValue(BigDecimal.valueOf(100000))
                .baseSalePrice(BigDecimal.valueOf(39000))
                .dynamicMinPrice(BigDecimal.valueOf(35000))
                .dynamicMaxPrice(BigDecimal.valueOf(45000))
                .platformFee(BigDecimal.valueOf(4000))
                .active(true)
                .build();
    }
}
