package com.LastBite.modules.order.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.security.SensitiveDataCipher;
import com.LastBite.modules.audit.service.OrderStatusHistoryService;
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
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.order.service.impl.OrderService;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.payment.enums.PaymentStatus;
import com.LastBite.modules.payment.service.PaymentService;
import com.LastBite.modules.promotion.service.VoucherApplicationResult;
import com.LastBite.modules.promotion.service.VoucherApplicationService;
import com.LastBite.modules.refund.service.RefundService;
import com.LastBite.modules.review.service.ReviewService;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.service.StoreCalendarService;
import com.LastBite.modules.store.service.StoreReliabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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
    private final PaymentService paymentService = mock(PaymentService.class);
    private final RefundService refundService = mock(RefundService.class);
    private final VoucherApplicationService voucherApplicationService = mock(VoucherApplicationService.class);
    private final StoreCalendarService storeCalendarService = mock(StoreCalendarService.class);
    private final StoreReliabilityService reliabilityService = mock(StoreReliabilityService.class);
    private final OrderStatusHistoryService statusHistoryService = mock(OrderStatusHistoryService.class);
    private final ReviewService reviewService = mock(ReviewService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T13:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BagPricingService pricingService = new BagPricingService(clock);
    private final SensitiveDataCipher sensitiveDataCipher = new SensitiveDataCipher("test-encryption-key");

    private OrderService service;
    private UUID userId;
    private UUID bagId;
    private User user;
    private BagDailyStock stock;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, stockRepository, auditLogRepository,
                userRepository, pricingService, notificationService, paymentService, refundService,
                voucherApplicationService, storeCalendarService, reliabilityService, statusHistoryService,
                sensitiveDataCipher, clock, reviewService);
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
        when(paymentService.findByOrderId(any())).thenReturn(Optional.empty());
        when(paymentService.createPaymentForOrder(any())).thenReturn(null);
        when(storeCalendarService.supportsPickupWindow(any(), any(), any(), any())).thenReturn(true);
        when(voucherApplicationService.reserveForOrder(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> VoucherApplicationResult.none(invocation.getArgument(2)));
    }

    @Test
    void createSnapshotsCurrentDynamicPriceAndReservesStock() {
        var response = service.create(userId, request(2, "idem-1"));

        assertEquals(BigDecimal.valueOf(35000), response.getUnitPrice());
        assertEquals(BigDecimal.valueOf(70000), response.getSubtotal());
        assertEquals(BigDecimal.valueOf(70000), response.getFinalAmount());
        assertNotNull(response.getPickupQrToken());
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
    void getReturnsPickupQrTokenWhenOrderHasEncryptedToken() {
        String qrToken = "pk_test_token";
        Order order = order(OrderStatus.PAID, LocalTime.of(23, 0), Instant.now(clock).plusSeconds(600));
        order.setPickupQrTokenEncrypted(sensitiveDataCipher.encrypt(qrToken));
        when(orderRepository.findByIdAndUserId(order.getId(), userId)).thenReturn(Optional.of(order));

        var response = service.get(userId, order.getId());

        assertEquals(qrToken, response.getPickupQrToken());
    }

    @Test
    void getReturnsNullPickupQrTokenForLegacyOrderWithoutEncryptedToken() {
        Order order = order(OrderStatus.PAID, LocalTime.of(23, 0), Instant.now(clock).plusSeconds(600));
        when(orderRepository.findByIdAndUserId(order.getId(), userId)).thenReturn(Optional.of(order));

        var response = service.get(userId, order.getId());

        assertNull(response.getPickupQrToken());
    }

    @Test
    void listDoesNotExposePickupQrToken() {
        Order order = order(OrderStatus.PAID, LocalTime.of(23, 0), Instant.now(clock).plusSeconds(600));
        order.setPickupQrTokenEncrypted(sensitiveDataCipher.encrypt("pk_should_not_be_listed"));
        when(orderRepository.searchCustomerOrders(eq(userId), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        var response = service.list(userId, null, null, null, null, PageRequest.of(0, 20));

        assertNull(response.content().get(0).getPickupQrToken());
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

    @Test
    void pendingPaymentCanBeCancelledBeforePaymentExpiryEvenInsidePickupCutoff() {
        Order order = order(OrderStatus.PENDING_PAYMENT, LocalTime.of(21, 0), Instant.now(clock).plusSeconds(600));
        Payment payment = payment(order, PaymentStatus.PENDING, Instant.now(clock).plusSeconds(600));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        var response = service.cancel(userId, order.getId());

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertEquals(0, stock.getReserved());
        verify(paymentService).cancelPendingPayment(payment, "Customer cancelled before payment");
        verify(refundService, never()).createAutoRefund(any(), any(), any(), any());
    }

    @Test
    void pendingPaymentCannotBeCancelledAfterPaymentExpiry() {
        Order order = order(OrderStatus.PENDING_PAYMENT, LocalTime.of(21, 0), Instant.now(clock).minusSeconds(1));
        Payment payment = payment(order, PaymentStatus.PENDING, Instant.now(clock).minusSeconds(1));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        assertThrows(ApiException.class, () -> service.cancel(userId, order.getId()));

        verify(paymentService, never()).cancelPendingPayment(any(), any());
        verify(refundService, never()).createAutoRefund(any(), any(), any(), any());
    }

    @Test
    void paidOrderCanBeCancelledBeforePickupCutoff() {
        Order order = order(OrderStatus.PAID, LocalTime.of(23, 0), Instant.now(clock).plusSeconds(600));
        Payment payment = payment(order, PaymentStatus.SUCCEEDED, Instant.now(clock).plusSeconds(600));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        var response = service.cancel(userId, order.getId());

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        verify(refundService).createAutoRefund(eq(order), eq(payment), any(), eq("Customer cancelled before pickup window"));
        verify(paymentService, never()).cancelPendingPayment(any(), any());
    }

    @Test
    void paidOrderCannotBeCancelledInsidePickupCutoff() {
        Order order = order(OrderStatus.PAID, LocalTime.of(21, 30), Instant.now(clock).plusSeconds(600));
        Payment payment = payment(order, PaymentStatus.SUCCEEDED, Instant.now(clock).plusSeconds(600));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentService.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        assertThrows(ApiException.class, () -> service.cancel(userId, order.getId()));

        verify(refundService, never()).createAutoRefund(any(), any(), any(), any());
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
                .paymentExpiresAt(Instant.now(clock).plusSeconds(600))
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private Order order(OrderStatus status, LocalTime pickupStartTime, Instant paymentExpiresAt) {
        Order order = Order.builder()
                .orderNumber("LB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
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
                .status(status)
                .pickupCode("ABC123")
                .pickupDate(LocalDate.of(2026, 5, 25))
                .pickupStartTime(pickupStartTime)
                .pickupEndTime(pickupStartTime.plusHours(1))
                .reservedUntil(paymentExpiresAt)
                .paymentExpiresAt(paymentExpiresAt)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    private Payment payment(Order order, PaymentStatus status, Instant expiresAt) {
        Payment payment = Payment.builder()
                .order(order)
                .user(user)
                .provider(PaymentProvider.FAKE)
                .providerOrderCode(1234567890L)
                .amount(order.getFinalAmount())
                .currency("VND")
                .status(status)
                .expiresAt(expiresAt)
                .idempotencyKey("pay:" + order.getId())
                .build();
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }
}
