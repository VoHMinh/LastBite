package com.LastBite.modules.order.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagStatus;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.StockAuditLogRepository;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.order.dto.request.CreateOrderRequest;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.order.service.impl.OrderService;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final BagDailyStockRepository stockRepository = mock(BagDailyStockRepository.class);
    private final StockAuditLogRepository auditLogRepository = mock(StockAuditLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationServicePort notificationService = mock(NotificationServicePort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T13:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BagPricingService pricingService = new BagPricingService(clock);

    private OrderService service;
    private UUID userId;
    private UUID bagId;
    private User user;
    private BagDailyStock stock;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, stockRepository, auditLogRepository,
                userRepository, pricingService, notificationService, clock);
        userId = UUID.randomUUID();
        bagId = UUID.randomUUID();
        user = User.builder().email("customer@test.com").fullName("Customer Test").build();
        ReflectionTestUtils.setField(user, "id", userId);
        stock = stock(5, 1, 0, DailyStockStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stockRepository.findByBagIdAndDateForUpdate(bagId, LocalDate.of(2026, 5, 25)))
                .thenReturn(Optional.of(stock));
        when(orderRepository.findByUser_IdAndIdempotencyKey(userId, "idem-1")).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createSnapshotsCurrentDynamicPriceAndReservesStock() {
        var response = service.create(userId, request(2, "idem-1"));

        assertEquals(BigDecimal.valueOf(35000), response.getUnitPrice());
        assertEquals(BigDecimal.valueOf(70000), response.getSubtotal());
        assertEquals(BigDecimal.valueOf(70000), response.getFinalAmount());
        assertEquals(3, stock.getReserved());
        verify(auditLogRepository).save(any());
    }

    @Test
    void createReturnsExistingOrderForSameIdempotencyKeyWithoutReservingAgain() {
        Order existing = existingOrder("idem-1");
        when(orderRepository.findByUser_IdAndIdempotencyKey(userId, "idem-1")).thenReturn(Optional.of(existing));

        var response = service.create(userId, request(2, "idem-1"));

        assertEquals(existing.getOrderNumber(), response.getOrderNumber());
        verify(stockRepository, never()).findByBagIdAndDateForUpdate(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createRejectsQuantityAboveBagLimit() {
        stock.getBag().setMaxPerOrder(1);

        assertThrows(ApiException.class, () -> service.create(userId, request(2, "idem-1")));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createRejectsSoldOutStock() {
        stock.setStatus(DailyStockStatus.SOLD_OUT);

        assertThrows(ApiException.class, () -> service.create(userId, request(1, "idem-1")));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createMarksStockSoldOutWhenReservationConsumesLastAvailableBag() {
        stock = stock(2, 1, 0, DailyStockStatus.ACTIVE);
        when(stockRepository.findByBagIdAndDateForUpdate(bagId, LocalDate.of(2026, 5, 25)))
                .thenReturn(Optional.of(stock));

        service.create(userId, request(1, "idem-1"));

        assertEquals(DailyStockStatus.SOLD_OUT, stock.getStatus());
    }

    private CreateOrderRequest request(int quantity, String idempotencyKey) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setBagId(bagId);
        request.setQuantity(quantity);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private BagDailyStock stock(int quantity, int reserved, int sold, DailyStockStatus status) {
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
                .category(StoreCategory.BAKERY)
                .bagSize(BagSize.STANDARD)
                .minimumValue(BigDecimal.valueOf(100000))
                .baseSalePrice(BigDecimal.valueOf(39000))
                .dynamicMinPrice(BigDecimal.valueOf(35000))
                .dynamicMaxPrice(BigDecimal.valueOf(45000))
                .dynamicPricingEnabled(true)
                .platformFee(BigDecimal.valueOf(4000))
                .maxPerOrder(3)
                .pickupStartTime(LocalTime.of(20, 0))
                .pickupEndTime(LocalTime.of(21, 0))
                .availableDays(new Integer[]{1, 2, 3, 4, 5})
                .status(BagStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(bag, "id", bagId);
        BagDailyStock stock = BagDailyStock.builder()
                .bag(bag)
                .store(store)
                .date(LocalDate.of(2026, 5, 25))
                .quantity(quantity)
                .reserved(reserved)
                .sold(sold)
                .status(status)
                .build();
        ReflectionTestUtils.setField(stock, "id", UUID.randomUUID());
        return stock;
    }

    private Order existingOrder(String idempotencyKey) {
        return Order.builder()
                .orderNumber("LB-EXISTING")
                .user(user)
                .store(stock.getStore())
                .bag(stock.getBag())
                .dailyStock(stock)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(39000))
                .platformFee(BigDecimal.valueOf(4000))
                .subtotal(BigDecimal.valueOf(39000))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(39000))
                .pickupCode("ABC123")
                .pickupDate(LocalDate.of(2026, 5, 25))
                .pickupStartTime(LocalTime.of(20, 0))
                .pickupEndTime(LocalTime.of(21, 0))
                .reservedUntil(Instant.now(clock).plusSeconds(600))
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
