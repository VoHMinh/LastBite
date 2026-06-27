package com.LastBite.modules.notification.service;

import com.LastBite.modules.auth.entity.Role;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.AuthProvider;
import com.LastBite.modules.auth.enums.RoleScope;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagStatus;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.merchant.entity.MerchantBusinessProfile;
import com.LastBite.modules.merchant.entity.MerchantStoreMember;
import com.LastBite.modules.merchant.enums.BusinessLegalType;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.notification.entity.AppNotification;
import com.LastBite.modules.notification.enums.NotificationType;
import com.LastBite.modules.notification.repository.AppNotificationRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final AppNotificationRepository notificationRepository = mock(AppNotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FavoriteStoreRepository favoriteStoreRepository = mock(FavoriteStoreRepository.class);
    private final MerchantStoreMemberRepository memberRepository = mock(MerchantStoreMemberRepository.class);
    private final NotificationDispatchService dispatchService = mock(NotificationDispatchService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T13:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private NotificationService service;
    private User owner;
    private User manager;
    private Store store;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userRepository, favoriteStoreRepository,
                memberRepository, dispatchService, clock);
        owner = user("owner@test.com", UserRole.MERCHANT_OWNER);
        manager = user("manager@test.com", UserRole.MANAGER);
        store = store(owner);

        when(notificationRepository.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any(AppNotification.class))).thenAnswer(invocation -> {
            AppNotification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        Role managerRole = Role.builder().code(UserRole.MANAGER).scope(RoleScope.STORE).build();
        when(memberRepository.findAllByStoreIdOrderByCreatedAtAsc(store.getId()))
                .thenReturn(List.of(MerchantStoreMember.builder()
                        .store(store)
                        .user(manager)
                        .role(managerRole)
                        .status(StoreMemberStatus.ACTIVE)
                        .build()));
    }

    @Test
    void tomorrowStockSummaryNotifiesOwnerAndManager() {
        service.notifyMerchantTomorrowStockSummary(store, LocalDate.of(2026, 5, 26), 8, 2);

        ArgumentCaptor<AppNotification> captor = ArgumentCaptor.forClass(AppNotification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(notification -> notification.getType() == NotificationType.MERCHANT_TOMORROW_STOCK_SUMMARY));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(notification -> notification.getRecipient().getId().equals(owner.getId())));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(notification -> notification.getRecipient().getId().equals(manager.getId())));
        assertTrue(captor.getAllValues().get(0).getDeepLink().contains("date=2026-05-26"));
    }

    @Test
    void soldOutBeforePickupEndSendsAddMoreNotification() {
        BagDailyStock stock = stock(2, 2, 0, LocalTime.of(21, 0));

        service.notifyMerchantStockLow(stock);

        ArgumentCaptor<AppNotification> captor = ArgumentCaptor.forClass(AppNotification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(notification -> notification.getType() == NotificationType.MERCHANT_SOLD_OUT));
        assertTrue(captor.getAllValues().get(0).getDeepLink().endsWith("/stock/today"));
    }

    private User user(String email, UserRole roleCode) {
        Role role = Role.builder()
                .code(roleCode)
                .scope(roleCode == UserRole.MERCHANT_OWNER ? RoleScope.PLATFORM : RoleScope.STORE)
                .build();
        User user = User.builder()
                .email(email)
                .fullName(email)
                .roles(Set.of(role))
                .accountType(AccountType.PLATFORM)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Store store(User owner) {
        MerchantBusinessProfile profile = MerchantBusinessProfile.builder()
                .owner(owner)
                .legalType(BusinessLegalType.INDIVIDUAL)
                .representativeFullName("Owner")
                .build();
        ReflectionTestUtils.setField(profile, "id", UUID.randomUUID());
        Store store = Store.builder()
                .businessProfile(profile)
                .createdBy(owner)
                .name("Test Store")
                .slug("test-store")
                .category(StoreCategory.BAKERY)
                .address("123 Test")
                .status(StoreStatus.ACTIVE)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        ReflectionTestUtils.setField(store, "id", UUID.randomUUID());
        return store;
    }

    private BagDailyStock stock(int quantity, int reserved, int sold, LocalTime pickupEnd) {
        SurpriseBag bag = SurpriseBag.builder()
                .store(store)
                .name("Evening Bag")
                .bagType(BagType.BREAD)
                .category(StoreCategory.BAKERY)
                .bagSize(BagSize.STANDARD)
                .minimumValue(BigDecimal.valueOf(100000))
                .baseSalePrice(BigDecimal.valueOf(39000))
                .dynamicMinPrice(BigDecimal.valueOf(35000))
                .dynamicMaxPrice(BigDecimal.valueOf(45000))
                .platformFee(BigDecimal.valueOf(4000))
                .pickupStartTime(LocalTime.of(20, 0))
                .pickupEndTime(pickupEnd)
                .availableDays(new Integer[]{1})
                .status(BagStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(bag, "id", UUID.randomUUID());
        BagDailyStock stock = BagDailyStock.builder()
                .bag(bag)
                .store(store)
                .date(LocalDate.of(2026, 5, 25))
                .quantity(quantity)
                .reserved(reserved)
                .sold(sold)
                .status(DailyStockStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(stock, "id", UUID.randomUUID());
        return stock;
    }
}
