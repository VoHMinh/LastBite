package com.LastBite.modules.accountdeletion.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.service.EmailService;
import com.LastBite.modules.accountdeletion.dto.request.CreateAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.dto.request.FinalizeAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.dto.request.PublicAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.dto.request.ReviewAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.dto.response.AccountDeletionEligibilityResponse;
import com.LastBite.modules.accountdeletion.dto.response.AccountDeletionResponse;
import com.LastBite.modules.accountdeletion.entity.AccountDeletionRequest;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequesterType;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequestStatus;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionSource;
import com.LastBite.modules.accountdeletion.repository.AccountDeletionRequestRepository;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.auth.service.JwtServicePort;
import com.LastBite.modules.auth.service.impl.RefreshTokenService;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.notification.repository.AppNotificationRepository;
import com.LastBite.modules.notification.repository.NotificationDeliveryRepository;
import com.LastBite.modules.notification.repository.NotificationDeviceRepository;
import com.LastBite.modules.notification.repository.NotificationPreferenceRepository;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.refund.enums.RefundStatus;
import com.LastBite.modules.refund.repository.RefundRequestRepository;
import com.LastBite.modules.settlement.enums.MerchantSettlementStatus;
import com.LastBite.modules.settlement.enums.PayoutStatus;
import com.LastBite.modules.settlement.repository.MerchantSettlementRepository;
import com.LastBite.modules.settlement.repository.StorePayoutRepository;
import com.LastBite.modules.store.repository.StoreRepository;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import com.LastBite.modules.user.repository.UserAddressRepository;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final int GRACE_DAYS = 30;
    private static final int WEB_TOKEN_HOURS = 24;
    private static final Set<AccountDeletionRequestStatus> ACTIVE_REQUEST_STATUSES = Set.of(
            AccountDeletionRequestStatus.PENDING_VERIFICATION,
            AccountDeletionRequestStatus.PENDING_REVIEW,
            AccountDeletionRequestStatus.BLOCKED,
            AccountDeletionRequestStatus.PENDING_FINALIZATION
    );
    private static final List<OrderStatus> OPEN_ORDER_STATUSES = List.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAID,
            OrderStatus.READY_FOR_PICKUP
    );
    private static final List<RefundStatus> OPEN_REFUND_STATUSES = List.of(
            RefundStatus.PENDING_REVIEW,
            RefundStatus.APPROVED,
            RefundStatus.PROCESSING,
            RefundStatus.FAILED
    );

    private final AccountDeletionRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final RefundRequestRepository refundRepository;
    private final MerchantSettlementRepository settlementRepository;
    private final StorePayoutRepository payoutRepository;
    private final StoreRepository storeRepository;
    private final SurpriseBagRepository bagRepository;
    private final MerchantStoreMemberRepository memberRepository;
    private final UserAddressRepository addressRepository;
    private final FavoriteStoreRepository favoriteStoreRepository;
    private final UserDiscoveryPreferenceRepository discoveryPreferenceRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationDeviceRepository notificationDeviceRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final AppNotificationRepository notificationRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtServicePort jwtService;
    private final EmailService emailService;

    @Value("${app.account-deletion.verification-url:http://localhost:3000/account-deletion/verify}")
    private String accountDeletionVerificationUrl;

    @Value("${app.account-deletion.cancellation-url:http://localhost:3000/account-deletion/cancel}")
    private String accountDeletionCancellationUrl;

    @Transactional(readOnly = true)
    public AccountDeletionEligibilityResponse eligibility(UUID userId) {
        User user = getUser(userId);
        AccountDeletionRequesterType type = requesterType(user);
        List<String> blockers = blockers(user, type);
        AccountDeletionResponse pending = requestRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, ACTIVE_REQUEST_STATUSES)
                .map(this::toResponse)
                .orElse(null);
        return AccountDeletionEligibilityResponse.builder()
                .eligible(blockers.isEmpty() && pending == null)
                .requesterType(type)
                .blockers(blockers)
                .pendingRequest(pending)
                .build();
    }

    @Transactional
    public AccountDeletionResponse requestAuthenticated(UUID userId, CreateAccountDeletionRequest input) {
        User user = getUser(userId);
        ensureCanStartRequest(user);
        AccountDeletionRequesterType type = requesterType(user);
        List<String> blockers = blockers(user, type);

        AccountDeletionRequest request = AccountDeletionRequest.builder()
                .user(user)
                .requestEmail(user.getEmail())
                .requestPhone(user.getPhone())
                .requesterType(type)
                .source(AccountDeletionSource.IN_APP)
                .reason(trimToNull(input == null ? null : input.getReason()))
                .status(initialStatus(type, blockers))
                .blockerSummary(joinBlockers(blockers))
                .verifiedAt(Instant.now())
                .build();

        String rawCancelToken = null;
        if (request.getStatus() == AccountDeletionRequestStatus.PENDING_FINALIZATION) {
            rawCancelToken = scheduleDeletion(user, request);
        }
        request = requestRepository.save(request);
        if (rawCancelToken != null) {
            sendScheduledEmail(user, rawCancelToken);
        }
        return toResponse(request);
    }

    @Transactional
    public void requestPublic(PublicAccountDeletionRequest input) {
        String email = input.getEmail().toLowerCase().trim();
        var maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            return;
        }
        User user = maybeUser.get();
        if (user.getStatus() == UserStatus.DELETED || user.getStatus() == UserStatus.PENDING_DELETION) {
            return;
        }
        if (requestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                user.getId(), ACTIVE_REQUEST_STATUSES).isPresent()) {
            return;
        }
        AccountDeletionRequesterType actualType = requesterType(user);
        if (input.getRequesterType() != actualType) {
            return;
        }

        String rawToken = jwtService.generateRefreshToken();
        String tokenHash = jwtService.hashToken(rawToken);
        AccountDeletionRequest request = AccountDeletionRequest.builder()
                .user(user)
                .requestEmail(email)
                .requestPhone(trimToNull(input.getPhone()))
                .requesterType(actualType)
                .source(AccountDeletionSource.WEB)
                .status(AccountDeletionRequestStatus.PENDING_VERIFICATION)
                .reason(trimToNull(input.getReason()))
                .tokenHash(tokenHash)
                .tokenExpiresAt(Instant.now().plusSeconds(WEB_TOKEN_HOURS * 3600L))
                .build();
        request = requestRepository.save(request);
        emailService.sendAccountDeletionVerificationEmail(
                email,
                user.getFullName(),
                buildVerificationLink(rawToken));
    }

    @Transactional
    public AccountDeletionResponse verifyPublic(String rawToken) {
        String tokenHash = jwtService.hashToken(rawToken);
        AccountDeletionRequest request = requestRepository
                .findByTokenHashAndStatusAndTokenExpiresAtAfter(
                        tokenHash,
                        AccountDeletionRequestStatus.PENDING_VERIFICATION,
                        Instant.now())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        User user = request.getUser();
        List<String> blockers = blockers(user, request.getRequesterType());
        request.setVerifiedAt(Instant.now());
        request.setTokenHash(null);
        request.setTokenExpiresAt(null);
        request.setStatus(initialStatus(request.getRequesterType(), blockers));
        request.setBlockerSummary(joinBlockers(blockers));
        String rawCancelToken = null;
        if (request.getStatus() == AccountDeletionRequestStatus.PENDING_FINALIZATION) {
            rawCancelToken = scheduleDeletion(user, request);
        }
        request = requestRepository.save(request);
        if (rawCancelToken != null) {
            sendScheduledEmail(user, rawCancelToken);
        }
        return toResponse(request);
    }

    @Transactional
    public AccountDeletionResponse cancel(UUID userId) {
        AccountDeletionRequest request = requestRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, ACTIVE_REQUEST_STATUSES)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return cancelRequest(request, "Cancelled by user");
    }

    @Transactional
    public AccountDeletionResponse cancelPublic(String rawToken) {
        String tokenHash = jwtService.hashToken(rawToken);
        AccountDeletionRequest request = requestRepository
                .findByTokenHashAndStatusInAndTokenExpiresAtAfter(
                        tokenHash,
                        Set.of(AccountDeletionRequestStatus.PENDING_FINALIZATION),
                        Instant.now())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        return cancelRequest(request, "Cancelled by deletion cancellation link");
    }

    @Transactional(readOnly = true)
    public Page<AccountDeletionResponse> searchAdmin(AccountDeletionRequestStatus status, Pageable pageable) {
        Page<AccountDeletionRequest> page = status == null
                ? requestRepository.findAll(pageable)
                : requestRepository.findAllByStatus(status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public AccountDeletionResponse approve(UUID adminId, UUID requestId, ReviewAccountDeletionRequest input) {
        AccountDeletionRequest request = getRequest(requestId);
        if (request.getStatus() != AccountDeletionRequestStatus.PENDING_REVIEW
                && request.getStatus() != AccountDeletionRequestStatus.BLOCKED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Request is not reviewable");
        }
        List<String> blockers = blockers(request.getUser(), request.getRequesterType());
        request.setReviewedBy(userRepository.getReferenceById(adminId));
        request.setAdminNote(trimToNull(input == null ? null : input.getAdminNote()));
        request.setBlockerSummary(joinBlockers(blockers));
        if (!blockers.isEmpty()) {
            request.setStatus(AccountDeletionRequestStatus.BLOCKED);
            return toResponse(requestRepository.save(request));
        }
        request.setStatus(AccountDeletionRequestStatus.PENDING_FINALIZATION);
        String rawCancelToken = scheduleDeletion(request.getUser(), request);
        request = requestRepository.save(request);
        sendScheduledEmail(request.getUser(), rawCancelToken);
        return toResponse(request);
    }

    @Transactional
    public AccountDeletionResponse reject(UUID adminId, UUID requestId, ReviewAccountDeletionRequest input) {
        AccountDeletionRequest request = getRequest(requestId);
        if (request.getStatus() == AccountDeletionRequestStatus.FINALIZED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Deletion request already finalized");
        }
        request.setStatus(AccountDeletionRequestStatus.REJECTED);
        request.setReviewedBy(userRepository.getReferenceById(adminId));
        request.setAdminNote(trimToNull(input == null ? null : input.getAdminNote()));
        request.setTokenHash(null);
        request.setTokenExpiresAt(null);
        resetPendingDeletion(request.getUser());
        return toResponse(requestRepository.save(request));
    }

    @Transactional
    public AccountDeletionResponse finalize(UUID adminId, UUID requestId, FinalizeAccountDeletionRequest input) {
        AccountDeletionRequest request = getRequest(requestId);
        if (request.getStatus() != AccountDeletionRequestStatus.PENDING_FINALIZATION) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Request is not ready for finalization");
        }
        boolean force = input != null && input.isForce();
        if (!force && request.getScheduledDeletionAt() != null
                && request.getScheduledDeletionAt().isAfter(Instant.now())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Deletion is still within grace period");
        }
        List<String> blockers = blockers(request.getUser(), request.getRequesterType());
        if (!blockers.isEmpty()) {
            request.setStatus(AccountDeletionRequestStatus.BLOCKED);
            request.setBlockerSummary(joinBlockers(blockers));
            return toResponse(requestRepository.save(request));
        }

        finalizeByType(request.getUser(), request.getRequesterType());
        request.setStatus(AccountDeletionRequestStatus.FINALIZED);
        request.setFinalizedAt(Instant.now());
        request.setTokenHash(null);
        request.setTokenExpiresAt(null);
        request.setReviewedBy(userRepository.getReferenceById(adminId));
        if (input != null && trimToNull(input.getAdminNote()) != null) {
            request.setAdminNote(trimToNull(input.getAdminNote()));
        }
        return toResponse(requestRepository.save(request));
    }

    private void ensureCanStartRequest(User user) {
        if (user.getStatus() == UserStatus.DELETED || user.getStatus() == UserStatus.PENDING_DELETION) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Account already has an active deletion flow");
        }
        requestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), ACTIVE_REQUEST_STATUSES)
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Account already has an active deletion request");
                });
    }

    private AccountDeletionRequestStatus initialStatus(AccountDeletionRequesterType type, List<String> blockers) {
        if (type == AccountDeletionRequesterType.MERCHANT_OWNER) {
            return blockers.isEmpty()
                    ? AccountDeletionRequestStatus.PENDING_REVIEW
                    : AccountDeletionRequestStatus.BLOCKED;
        }
        return blockers.isEmpty()
                ? AccountDeletionRequestStatus.PENDING_FINALIZATION
                : AccountDeletionRequestStatus.BLOCKED;
    }

    private String scheduleDeletion(User user, AccountDeletionRequest request) {
        Instant now = Instant.now();
        Instant scheduled = now.plusSeconds(GRACE_DAYS * 24L * 3600L);
        String rawCancelToken = jwtService.generateRefreshToken();
        request.setScheduledDeletionAt(scheduled);
        request.setTokenHash(jwtService.hashToken(rawCancelToken));
        request.setTokenExpiresAt(scheduled);
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(now);
        user.setDeletionScheduledAt(scheduled);
        userRepository.save(user);
        refreshTokenService.revokeAllByUserId(user.getId());
        return rawCancelToken;
    }

    private void finalizeByType(User user, AccountDeletionRequesterType type) {
        switch (type) {
            case CUSTOMER, ADMIN -> {
                deleteProfileOnlyData(user.getId());
                anonymizeUser(user);
            }
            case STORE_MEMBER -> {
                memberRepository.findByUserId(user.getId()).ifPresent(member -> {
                    member.setStatus(StoreMemberStatus.TERMINATED);
                    memberRepository.save(member);
                });
                deleteProfileOnlyData(user.getId());
                anonymizeUser(user);
            }
            case MERCHANT_OWNER -> {
                bagRepository.archiveAllByOwnerId(user.getId());
                memberRepository.terminateAllByOwnerId(user.getId());
                storeRepository.closeAllByOwnerId(user.getId());
                deleteProfileOnlyData(user.getId());
                anonymizeUser(user);
            }
        }
    }

    private void deleteProfileOnlyData(UUID userId) {
        notificationDeliveryRepository.deleteByNotificationRecipientId(userId);
        notificationRepository.deleteByRecipientId(userId);
        notificationPreferenceRepository.deleteByUserId(userId);
        notificationDeviceRepository.deactivateAllByUserId(userId);
        discoveryPreferenceRepository.deleteByUserId(userId);
        favoriteStoreRepository.deleteByUserId(userId);
        addressRepository.deleteByUserId(userId);
        refreshTokenService.revokeAllByUserId(userId);
    }

    private void anonymizeUser(User user) {
        String suffix = user.getId().toString().replace("-", "");
        user.setEmail("deleted+" + suffix + "@lastbite.local");
        user.setUsername("deleted_" + suffix.substring(0, 24));
        user.setFullName("Deleted User");
        user.setPhone(null);
        user.setAvatarUrl(null);
        user.setPasswordHash(null);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setMustChangePassword(false);
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(Instant.now());
        user.setAnonymizedAt(Instant.now());
        userRepository.save(user);
    }

    private AccountDeletionResponse cancelRequest(AccountDeletionRequest request, String note) {
        if (request.getStatus() == AccountDeletionRequestStatus.FINALIZED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Deletion request already finalized");
        }
        request.setStatus(AccountDeletionRequestStatus.CANCELLED);
        request.setAdminNote(note);
        request.setTokenHash(null);
        request.setTokenExpiresAt(null);
        resetPendingDeletion(request.getUser());
        return toResponse(requestRepository.save(request));
    }

    private void resetPendingDeletion(User user) {
        if (user.getStatus() == UserStatus.PENDING_DELETION) {
            user.setStatus(UserStatus.ACTIVE);
            user.setDeletionRequestedAt(null);
            user.setDeletionScheduledAt(null);
            userRepository.save(user);
        }
    }

    private void sendScheduledEmail(User user, String rawCancelToken) {
        if (trimToNull(user.getEmail()) == null) {
            return;
        }
        emailService.sendAccountDeletionScheduledEmail(
                user.getEmail(),
                user.getFullName(),
                buildCancellationLink(rawCancelToken),
                user.getDeletionScheduledAt());
    }

    private List<String> blockers(User user, AccountDeletionRequesterType type) {
        List<String> result = new ArrayList<>();
        if (type == AccountDeletionRequesterType.MERCHANT_OWNER) {
            long openOrders = orderRepository.countByOwnerIdAndStatusIn(user.getId(), OPEN_ORDER_STATUSES);
            long openRefunds = refundRepository.countByOwnerIdAndStatusIn(user.getId(), OPEN_REFUND_STATUSES);
            long openSettlements = settlementRepository.countByBusinessProfileOwnerIdAndStatusIn(
                    user.getId(),
                    List.of(MerchantSettlementStatus.DRAFT,
                            MerchantSettlementStatus.APPROVED,
                            MerchantSettlementStatus.PAYOUT_PROCESSING,
                            MerchantSettlementStatus.FAILED));
            long openPayouts = payoutRepository.countBySettlementBusinessProfileOwnerIdAndStatusIn(
                    user.getId(),
                    List.of(PayoutStatus.PENDING, PayoutStatus.PROCESSING, PayoutStatus.FAILED));
            addBlocker(result, openOrders, "open store order(s)");
            addBlocker(result, openRefunds, "open store refund request(s)");
            addBlocker(result, openSettlements, "open merchant settlement(s)");
            addBlocker(result, openPayouts, "open store payout(s)");
            return result;
        }
        long openOrders = orderRepository.countByUser_IdAndStatusIn(user.getId(), OPEN_ORDER_STATUSES);
        long openRefunds = refundRepository.countByRequestedBy_IdAndStatusIn(user.getId(), OPEN_REFUND_STATUSES);
        addBlocker(result, openOrders, "open order(s)");
        addBlocker(result, openRefunds, "open refund request(s)");
        return result;
    }

    private void addBlocker(List<String> result, long count, String label) {
        if (count > 0) {
            result.add(count + " " + label);
        }
    }

    private AccountDeletionRequesterType requesterType(User user) {
        if (user.getAccountType() == AccountType.STORE_MEMBER) {
            return AccountDeletionRequesterType.STORE_MEMBER;
        }
        if (user.hasRole(UserRole.MERCHANT_OWNER)) {
            return AccountDeletionRequesterType.MERCHANT_OWNER;
        }
        if (user.hasRole(UserRole.ADMIN)) {
            return AccountDeletionRequesterType.ADMIN;
        }
        return AccountDeletionRequesterType.CUSTOMER;
    }

    private String joinBlockers(List<String> blockers) {
        return blockers == null || blockers.isEmpty() ? null : String.join("; ", blockers);
    }

    private String buildVerificationLink(String rawToken) {
        String separator = accountDeletionVerificationUrl.contains("?") ? "&" : "?";
        return accountDeletionVerificationUrl + separator + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String buildCancellationLink(String rawToken) {
        String separator = accountDeletionCancellationUrl.contains("?") ? "&" : "?";
        return accountDeletionCancellationUrl + separator + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private AccountDeletionRequest getRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AccountDeletionResponse toResponse(AccountDeletionRequest request) {
        return AccountDeletionResponse.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .email(request.getRequestEmail())
                .requesterType(request.getRequesterType())
                .source(request.getSource())
                .status(request.getStatus())
                .reason(request.getReason())
                .blockerSummary(request.getBlockerSummary())
                .requestedAt(request.getCreatedAt())
                .verifiedAt(request.getVerifiedAt())
                .scheduledDeletionAt(request.getScheduledDeletionAt())
                .finalizedAt(request.getFinalizedAt())
                .adminNote(request.getAdminNote())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
