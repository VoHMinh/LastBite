package com.LastBite.modules.promotion.service;

import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagStatus;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.promotion.dto.request.ValidateVoucherRequest;
import com.LastBite.modules.promotion.entity.VoucherCampaign;
import com.LastBite.modules.promotion.entity.VoucherCode;
import com.LastBite.modules.promotion.entity.VoucherRedemption;
import com.LastBite.modules.promotion.enums.*;
import com.LastBite.modules.promotion.repository.UserVoucherRepository;
import com.LastBite.modules.promotion.repository.VoucherCampaignRepository;
import com.LastBite.modules.promotion.repository.VoucherCodeRepository;
import com.LastBite.modules.promotion.repository.VoucherRedemptionRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoucherApplicationServiceTest {

    private final VoucherCampaignRepository campaignRepository = mock(VoucherCampaignRepository.class);
    private final VoucherCodeRepository codeRepository = mock(VoucherCodeRepository.class);
    private final UserVoucherRepository userVoucherRepository = mock(UserVoucherRepository.class);
    private final VoucherRedemptionRepository redemptionRepository = mock(VoucherRedemptionRepository.class);
    private final SurpriseBagRepository bagRepository = mock(SurpriseBagRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-19T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BagPricingService pricingService = new BagPricingService(clock);

    private VoucherApplicationService service;
    private UUID userId;
    private UUID bagId;
    private User user;
    private Store store;
    private SurpriseBag bag;
    private VoucherCampaign campaign;
    private VoucherCode code;

    @BeforeEach
    void setUp() {
        service = new VoucherApplicationService(campaignRepository, codeRepository, userVoucherRepository,
                redemptionRepository, bagRepository, userRepository, orderRepository, pricingService, clock);
        userId = UUID.randomUUID();
        bagId = UUID.randomUUID();
        user = User.builder().email("customer@test.com").fullName("Customer").build();
        ReflectionTestUtils.setField(user, "id", userId);
        store = Store.builder()
                .name("Test Store")
                .slug("test-store")
                .category(StoreCategory.BAKERY)
                .address("Address")
                .build();
        ReflectionTestUtils.setField(store, "id", UUID.randomUUID());
        bag = SurpriseBag.builder()
                .store(store)
                .name("Dinner Bag")
                .bagType(BagType.MEAL)
                .dietType(DietType.MEAT)
                .category(StoreCategory.RESTAURANT)
                .bagSize(BagSize.STANDARD)
                .minimumValue(BigDecimal.valueOf(100000))
                .baseSalePrice(BigDecimal.valueOf(50000))
                .dynamicMinPrice(BigDecimal.valueOf(50000))
                .dynamicMaxPrice(BigDecimal.valueOf(50000))
                .dynamicPricingEnabled(false)
                .platformFee(BigDecimal.valueOf(4000))
                .maxPerOrder(3)
                .pickupStartTime(LocalTime.of(18, 0))
                .pickupEndTime(LocalTime.of(20, 0))
                .availableDays(new Integer[]{1, 2, 3, 4, 5, 6, 7})
                .status(BagStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(bag, "id", bagId);
        campaign = campaign(VoucherFundingSource.PLATFORM);
        code = VoucherCode.builder()
                .campaign(campaign)
                .code("SAVE20")
                .codeType(VoucherCodeType.PUBLIC)
                .status(VoucherCodeStatus.ACTIVE)
                .usageLimit(10)
                .build();
        ReflectionTestUtils.setField(code, "id", UUID.randomUUID());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(bagRepository.findById(bagId)).thenReturn(Optional.of(bag));
        when(codeRepository.findByCode("SAVE20")).thenReturn(Optional.of(code));
        when(codeRepository.findByCodeForUpdate("SAVE20")).thenReturn(Optional.of(code));
        when(campaignRepository.findByIdForUpdate(campaign.getId())).thenReturn(Optional.of(campaign));
        when(redemptionRepository.countUserCampaignUsage(eq(userId), eq(campaign.getId()), any())).thenReturn(0L);
        when(orderRepository.countByUser_IdAndPaidAtIsNotNull(userId)).thenReturn(0L);
        when(redemptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validatePlatformFundedFixedVoucherDoesNotChargeMerchantFunding() {
        ValidateVoucherRequest request = new ValidateVoucherRequest();
        request.setBagId(bagId);
        request.setQuantity(2);
        request.setVoucherCode("SAVE20");

        var response = service.validate(userId, request);

        assertEquals(BigDecimal.valueOf(100000), response.getSubtotalAmount());
        assertEquals(BigDecimal.valueOf(20000).setScale(0), response.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(20000).setScale(0), response.getPlatformFundedAmount());
        assertEquals(BigDecimal.ZERO.setScale(0), response.getMerchantFundedAmount());
        assertEquals(BigDecimal.valueOf(80000), response.getFinalAmount());
    }

    @Test
    void reserveCreatesSnapshotAndIncrementsCampaignAndCodeReservedCounters() {
        Order order = Order.builder()
                .orderNumber("LB-TEST")
                .user(user)
                .store(store)
                .bag(bag)
                .quantity(2)
                .subtotal(BigDecimal.valueOf(100000))
                .finalAmount(BigDecimal.valueOf(100000))
                .build();
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());

        VoucherApplicationResult result = service.reserveForOrder(user, bag, BigDecimal.valueOf(100000),
                order, "SAVE20", null, Instant.now(clock).plusSeconds(600));

        assertEquals(BigDecimal.valueOf(20000).setScale(0), result.getDiscountAmount());
        assertEquals(1, campaign.getReservedCount());
        assertEquals(BigDecimal.valueOf(20000).setScale(0), campaign.getReservedBudgetAmount());
        assertEquals(1, code.getReservedCount());

        ArgumentCaptor<VoucherRedemption> captor = ArgumentCaptor.forClass(VoucherRedemption.class);
        verify(redemptionRepository).save(captor.capture());
        assertEquals(VoucherRedemptionStatus.RESERVED, captor.getValue().getStatus());
        assertEquals(BigDecimal.valueOf(20000).setScale(0), captor.getValue().getPlatformFundedAmount());
    }

    private VoucherCampaign campaign(VoucherFundingSource fundingSource) {
        VoucherCampaign value = VoucherCampaign.builder()
                .ownerType(VoucherCampaignOwnerType.PLATFORM)
                .status(VoucherCampaignStatus.ACTIVE)
                .name("Launch voucher")
                .discountType(VoucherDiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(20000))
                .minOrderAmount(BigDecimal.ZERO)
                .fundingSource(fundingSource)
                .platformFundingBps(fundingSource == VoucherFundingSource.PLATFORM ? 10000 : 0)
                .merchantFundingBps(fundingSource == VoucherFundingSource.PLATFORM ? 0 : 10000)
                .startsAt(Instant.now(clock).minusSeconds(3600))
                .endsAt(Instant.now(clock).plusSeconds(3600))
                .perUserLimit(1)
                .build();
        ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        return value;
    }
}
