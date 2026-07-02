package com.LastBite.modules.accountdeletion.service;

import com.LastBite.common.service.EmailService;
import com.LastBite.modules.accountdeletion.dto.request.CreateAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.entity.AccountDeletionRequest;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequesterType;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequestStatus;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionSource;
import com.LastBite.modules.accountdeletion.repository.AccountDeletionRequestRepository;
import com.LastBite.modules.auth.entity.Role;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.RoleScope;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.auth.service.JwtServicePort;
import com.LastBite.modules.auth.service.impl.RefreshTokenService;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.notification.repository.AppNotificationRepository;
import com.LastBite.modules.notification.repository.NotificationDeliveryRepository;
import com.LastBite.modules.notification.repository.NotificationDeviceRepository;
import com.LastBite.modules.notification.repository.NotificationPreferenceRepository;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.refund.repository.RefundRequestRepository;
import com.LastBite.modules.settlement.repository.MerchantSettlementRepository;
import com.LastBite.modules.settlement.repository.StorePayoutRepository;
import com.LastBite.modules.store.repository.StoreRepository;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import com.LastBite.modules.user.repository.UserAddressRepository;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountDeletionServiceTest {

    private final AccountDeletionRequestRepository requestRepository = mock(AccountDeletionRequestRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final RefundRequestRepository refundRepository = mock(RefundRequestRepository.class);
    private final MerchantSettlementRepository settlementRepository = mock(MerchantSettlementRepository.class);
    private final StorePayoutRepository payoutRepository = mock(StorePayoutRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final SurpriseBagRepository bagRepository = mock(SurpriseBagRepository.class);
    private final MerchantStoreMemberRepository memberRepository = mock(MerchantStoreMemberRepository.class);
    private final UserAddressRepository addressRepository = mock(UserAddressRepository.class);
    private final FavoriteStoreRepository favoriteStoreRepository = mock(FavoriteStoreRepository.class);
    private final UserDiscoveryPreferenceRepository discoveryPreferenceRepository = mock(UserDiscoveryPreferenceRepository.class);
    private final NotificationPreferenceRepository notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
    private final NotificationDeviceRepository notificationDeviceRepository = mock(NotificationDeviceRepository.class);
    private final NotificationDeliveryRepository notificationDeliveryRepository = mock(NotificationDeliveryRepository.class);
    private final AppNotificationRepository notificationRepository = mock(AppNotificationRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final JwtServicePort jwtService = mock(JwtServicePort.class);
    private final EmailService emailService = mock(EmailService.class);

    private final AccountDeletionService service = new AccountDeletionService(
            requestRepository,
            userRepository,
            orderRepository,
            refundRepository,
            settlementRepository,
            payoutRepository,
            storeRepository,
            bagRepository,
            memberRepository,
            addressRepository,
            favoriteStoreRepository,
            discoveryPreferenceRepository,
            notificationPreferenceRepository,
            notificationDeviceRepository,
            notificationDeliveryRepository,
            notificationRepository,
            refreshTokenService,
            jwtService,
            emailService);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                service,
                "accountDeletionVerificationUrl",
                "http://localhost:3000/account-deletion/verify");
        ReflectionTestUtils.setField(
                service,
                "accountDeletionCancellationUrl",
                "http://localhost:3000/account-deletion/cancel");
    }

    @Test
    void customerWithoutBlockersIsScheduledAndCanBeCancelledByEmailLink() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, UserRole.CUSTOMER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(requestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(jwtService.generateRefreshToken()).thenReturn("cancel-token");
        when(jwtService.hashToken("cancel-token")).thenReturn("hashed-cancel-token");
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.requestAuthenticated(userId, new CreateAccountDeletionRequest());

        assertEquals(AccountDeletionRequestStatus.PENDING_FINALIZATION, response.getStatus());
        assertEquals(UserStatus.PENDING_DELETION, user.getStatus());
        assertNotNull(user.getDeletionScheduledAt());
        assertNotNull(response.getScheduledDeletionAt());
        verify(refreshTokenService).revokeAllByUserId(userId);
        verify(notificationDeviceRepository, never()).deactivateAllByUserId(userId);
        verify(emailService).sendAccountDeletionScheduledEmail(
                eq(user.getEmail()),
                eq(user.getFullName()),
                contains("token=cancel-token"),
                eq(user.getDeletionScheduledAt()));
    }

    @Test
    void merchantWithOpenOrdersIsBlockedAndNotLoggedOut() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, UserRole.MERCHANT_OWNER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(requestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(orderRepository.countByOwnerIdAndStatusIn(eq(userId), any())).thenReturn(2L);
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.requestAuthenticated(userId, new CreateAccountDeletionRequest());

        assertEquals(AccountDeletionRequestStatus.BLOCKED, response.getStatus());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(refreshTokenService, never()).revokeAllByUserId(userId);
        verify(notificationDeviceRepository, never()).deactivateAllByUserId(userId);
    }

    @Test
    void cancelPublicRestoresPendingDeletionUserAndInvalidatesToken() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, UserRole.CUSTOMER);
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(Instant.now());
        user.setDeletionScheduledAt(Instant.now().plusSeconds(3600));
        AccountDeletionRequest request = AccountDeletionRequest.builder()
                .user(user)
                .requestEmail(user.getEmail())
                .requesterType(AccountDeletionRequesterType.CUSTOMER)
                .source(AccountDeletionSource.IN_APP)
                .status(AccountDeletionRequestStatus.PENDING_FINALIZATION)
                .tokenHash("hashed-cancel-token")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .scheduledDeletionAt(user.getDeletionScheduledAt())
                .build();
        when(jwtService.hashToken("cancel-token")).thenReturn("hashed-cancel-token");
        when(requestRepository.findByTokenHashAndStatusInAndTokenExpiresAtAfter(
                eq("hashed-cancel-token"), any(), any())).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.cancelPublic("cancel-token");

        assertEquals(AccountDeletionRequestStatus.CANCELLED, response.getStatus());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(null, user.getDeletionRequestedAt());
        assertEquals(null, user.getDeletionScheduledAt());
        assertEquals(null, request.getTokenHash());
        assertEquals(null, request.getTokenExpiresAt());
        verify(userRepository).save(user);
    }

    private User user(UUID userId, UserRole roleCode) {
        Role role = Role.builder()
                .code(roleCode)
                .scope(RoleScope.PLATFORM)
                .build();
        User user = User.builder()
                .email(roleCode.name().toLowerCase() + "@test.local")
                .fullName("Test User")
                .accountType(AccountType.PLATFORM)
                .status(UserStatus.ACTIVE)
                .build();
        user.addRole(role);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
