package com.LastBite.modules.promotion.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.promotion.dto.request.ClaimVoucherRequest;
import com.LastBite.modules.promotion.dto.request.ValidateVoucherRequest;
import com.LastBite.modules.promotion.dto.response.UserVoucherResponse;
import com.LastBite.modules.promotion.dto.response.VoucherValidationResponse;
import com.LastBite.modules.promotion.entity.UserVoucher;
import com.LastBite.modules.promotion.entity.VoucherCampaign;
import com.LastBite.modules.promotion.entity.VoucherCode;
import com.LastBite.modules.promotion.entity.VoucherRedemption;
import com.LastBite.modules.promotion.enums.*;
import com.LastBite.modules.promotion.repository.UserVoucherRepository;
import com.LastBite.modules.promotion.repository.VoucherCampaignRepository;
import com.LastBite.modules.promotion.repository.VoucherCodeRepository;
import com.LastBite.modules.promotion.repository.VoucherRedemptionRepository;
import com.LastBite.modules.refund.enums.RefundReason;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VoucherApplicationService {

    private static final BigDecimal MIN_PAYABLE = BigDecimal.valueOf(1000);
    private static final List<VoucherRedemptionStatus> ACTIVE_USAGE_STATUSES =
            List.of(VoucherRedemptionStatus.RESERVED, VoucherRedemptionStatus.REDEEMED);
    private static final Set<RefundReason> REISSUE_REASONS = Set.of(
            RefundReason.STORE_CANCELLED,
            RefundReason.STORE_NO_STOCK,
            RefundReason.PAYMENT_AFTER_EXPIRY,
            RefundReason.PLATFORM_ERROR
    );

    private final VoucherCampaignRepository campaignRepository;
    private final VoucherCodeRepository codeRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final SurpriseBagRepository bagRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final BagPricingService pricingService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<VoucherValidationResponse> available(UUID userId, UUID bagId, int quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        SurpriseBag bag = bagRepository.findById(bagId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
        BigDecimal subtotal = subtotal(bag, quantity);
        Instant now = Instant.now(clock);
        List<VoucherCampaign> campaigns = campaignRepository.findEligibleForBag(
                bag.getStore().getId(), bag.getId(), bag.getCategory(), bag.getBagType(), bag.getDietType(), now);
        if (campaigns.isEmpty()) {
            return List.of();
        }

        Map<UUID, VoucherCampaign> campaignById = new HashMap<>();
        campaigns.forEach(campaign -> campaignById.put(campaign.getId(), campaign));
        List<VoucherCandidate> candidates = new ArrayList<>();

        List<UUID> campaignIds = campaigns.stream().map(VoucherCampaign::getId).toList();
        codeRepository.findActivePublicCodes(campaignIds, VoucherCodeType.PUBLIC, VoucherCodeStatus.ACTIVE, now)
                .forEach(code -> candidates.add(new VoucherCandidate(campaignById.get(code.getCampaign().getId()), code, null)));
        userVoucherRepository.findClaimedForCampaigns(userId, campaignIds, now)
                .forEach(userVoucher -> candidates.add(new VoucherCandidate(
                        campaignById.get(userVoucher.getCampaign().getId()), userVoucher.getCode(), userVoucher)));

        return candidates.stream()
                .map(candidate -> {
                    try {
                        return quote(user, bag, subtotal, candidate, false);
                    } catch (ApiException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(VoucherValidationResponse::getDiscountAmount).reversed()
                        .thenComparing(VoucherValidationResponse::getFinalAmount))
                .toList();
    }

    @Transactional(readOnly = true)
    public VoucherValidationResponse validate(UUID userId, ValidateVoucherRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        SurpriseBag bag = bagRepository.findById(request.getBagId())
                .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
        BigDecimal subtotal = subtotal(bag, request.getQuantity());
        VoucherCandidate candidate = loadCandidate(userId, request.getVoucherCode(), request.getUserVoucherId(), false);
        return quote(user, bag, subtotal, candidate, true);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public UserVoucherResponse claim(UUID userId, ClaimVoucherRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        VoucherCode code = codeRepository.findByCodeForUpdate(normalizeCode(request.getVoucherCode()))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Ma voucher khong ton tai"));
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(code.getCampaign().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        validateCampaignIsClaimable(campaign, code, Instant.now(clock));
        if (userVoucherRepository.existsByUserIdAndCodeIdAndStatusIn(userId, code.getId(),
                List.of(UserVoucherStatus.CLAIMED, UserVoucherStatus.RESERVED, UserVoucherStatus.REDEEMED))) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Ban da claim ma voucher nay");
        }
        UserVoucher userVoucher = userVoucherRepository.save(UserVoucher.builder()
                .user(user)
                .campaign(campaign)
                .code(code)
                .status(UserVoucherStatus.CLAIMED)
                .expiresAt(earliestExpiry(campaign, code))
                .build());
        return toUserVoucherResponse(userVoucher);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserVoucherResponse> wallet(UUID userId, UserVoucherStatus status, Pageable pageable) {
        var page = userVoucherRepository.searchWallet(userId, status, pageable)
                .map(this::toUserVoucherResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public VoucherApplicationResult reserveForOrder(User user, SurpriseBag bag, BigDecimal subtotal, Order order,
                                                    String voucherCode, UUID userVoucherId, Instant reservedUntil) {
        if ((voucherCode == null || voucherCode.isBlank()) && userVoucherId == null) {
            return VoucherApplicationResult.none(subtotal);
        }
        VoucherCandidate candidate = loadCandidate(user.getId(), voucherCode, userVoucherId, true);
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(candidate.campaign().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        candidate = new VoucherCandidate(campaign, candidate.code(), candidate.userVoucher());
        VoucherValidationResponse quote = quote(user, bag, subtotal, candidate, true);

        campaign.setReservedCount(campaign.getReservedCount() + 1);
        campaign.setReservedBudgetAmount(campaign.getReservedBudgetAmount().add(quote.getDiscountAmount()));
        if (candidate.code() != null) {
            candidate.code().setReservedCount(candidate.code().getReservedCount() + 1);
        }
        if (candidate.userVoucher() != null) {
            UserVoucher userVoucher = candidate.userVoucher();
            userVoucher.setStatus(UserVoucherStatus.RESERVED);
            userVoucher.setReservedOrder(order);
            userVoucher.setReservedUntil(reservedUntil);
        }

        redemptionRepository.save(VoucherRedemption.builder()
                .order(order)
                .user(user)
                .campaign(campaign)
                .code(candidate.code())
                .userVoucher(candidate.userVoucher())
                .codeSnapshot(candidate.code() == null ? null : candidate.code().getCode())
                .discountType(campaign.getDiscountType())
                .discountValue(campaign.getDiscountValue())
                .fundingSource(campaign.getFundingSource())
                .status(VoucherRedemptionStatus.RESERVED)
                .subtotalAmount(subtotal)
                .discountAmount(quote.getDiscountAmount())
                .platformFundedAmount(quote.getPlatformFundedAmount())
                .merchantFundedAmount(quote.getMerchantFundedAmount())
                .reservedAt(Instant.now(clock))
                .reservedUntil(reservedUntil)
                .build());

        return VoucherApplicationResult.builder()
                .campaignId(quote.getCampaignId())
                .voucherCodeId(quote.getVoucherCodeId())
                .userVoucherId(quote.getUserVoucherId())
                .voucherCode(quote.getVoucherCode())
                .campaignName(quote.getCampaignName())
                .fundingSource(quote.getFundingSource())
                .subtotalAmount(quote.getSubtotalAmount())
                .discountAmount(quote.getDiscountAmount())
                .platformFundedAmount(quote.getPlatformFundedAmount())
                .merchantFundedAmount(quote.getMerchantFundedAmount())
                .finalAmount(quote.getFinalAmount())
                .build();
    }

    @Transactional
    public void redeemForOrder(Order order) {
        VoucherRedemption redemption = redemptionRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (redemption == null || redemption.getStatus() == VoucherRedemptionStatus.REDEEMED) {
            return;
        }
        if (redemption.getStatus() != VoucherRedemptionStatus.RESERVED) {
            return;
        }
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(redemption.getCampaign().getId())
                .orElseThrow();
        campaign.setReservedCount(Math.max(0, campaign.getReservedCount() - 1));
        campaign.setReservedBudgetAmount(nonNegative(campaign.getReservedBudgetAmount().subtract(redemption.getDiscountAmount())));
        campaign.setRedeemedCount(campaign.getRedeemedCount() + 1);
        campaign.setRedeemedBudgetAmount(campaign.getRedeemedBudgetAmount().add(redemption.getDiscountAmount()));
        if (redemption.getCode() != null) {
            redemption.getCode().setReservedCount(Math.max(0, redemption.getCode().getReservedCount() - 1));
            redemption.getCode().setRedeemedCount(redemption.getCode().getRedeemedCount() + 1);
        }
        if (redemption.getUserVoucher() != null) {
            UserVoucher userVoucher = redemption.getUserVoucher();
            userVoucher.setStatus(UserVoucherStatus.REDEEMED);
            userVoucher.setRedeemedOrder(order);
            userVoucher.setRedeemedAt(Instant.now(clock));
        }
        redemption.setStatus(VoucherRedemptionStatus.REDEEMED);
        redemption.setRedeemedAt(Instant.now(clock));
    }

    @Transactional
    public void releaseForOrder(Order order, String reason) {
        VoucherRedemption redemption = redemptionRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (redemption == null || redemption.getStatus() != VoucherRedemptionStatus.RESERVED) {
            return;
        }
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(redemption.getCampaign().getId())
                .orElseThrow();
        campaign.setReservedCount(Math.max(0, campaign.getReservedCount() - 1));
        campaign.setReservedBudgetAmount(nonNegative(campaign.getReservedBudgetAmount().subtract(redemption.getDiscountAmount())));
        if (redemption.getCode() != null) {
            redemption.getCode().setReservedCount(Math.max(0, redemption.getCode().getReservedCount() - 1));
        }
        if (redemption.getUserVoucher() != null) {
            UserVoucher userVoucher = redemption.getUserVoucher();
            userVoucher.setStatus(UserVoucherStatus.CLAIMED);
            userVoucher.setReservedOrder(null);
            userVoucher.setReservedUntil(null);
        }
        redemption.setStatus(VoucherRedemptionStatus.RELEASED);
        redemption.setReleasedAt(Instant.now(clock));
        redemption.setReleaseReason(reason);
    }

    @Transactional
    public void reissueAfterFullRefundIfEligible(Order order, RefundReason reason) {
        if (!REISSUE_REASONS.contains(reason)) {
            return;
        }
        VoucherRedemption redemption = redemptionRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (redemption == null || redemption.getStatus() != VoucherRedemptionStatus.REDEEMED) {
            return;
        }
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(redemption.getCampaign().getId())
                .orElseThrow();
        Instant now = Instant.now(clock);
        campaign.setRedeemedCount(Math.max(0, campaign.getRedeemedCount() - 1));
        campaign.setRedeemedBudgetAmount(nonNegative(campaign.getRedeemedBudgetAmount().subtract(redemption.getDiscountAmount())));
        if (redemption.getCode() != null) {
            redemption.getCode().setRedeemedCount(Math.max(0, redemption.getCode().getRedeemedCount() - 1));
        }
        if (campaign.getEndsAt().isAfter(now)) {
            userVoucherRepository.save(UserVoucher.builder()
                    .user(order.getUser())
                    .campaign(campaign)
                    .code(redemption.getCode())
                    .status(UserVoucherStatus.CLAIMED)
                    .expiresAt(earliestExpiry(campaign, redemption.getCode()))
                    .build());
            redemption.setReissuedAt(now);
        }
        redemption.setStatus(VoucherRedemptionStatus.CANCELLED);
        redemption.setReleasedAt(now);
        redemption.setReleaseReason("Full refund reissued voucher: " + reason);
    }

    @Transactional(readOnly = true)
    public VoucherValidationResponse snapshotForOrder(UUID orderId) {
        return redemptionRepository.findByOrderId(orderId)
                .map(this::toValidationResponse)
                .orElse(null);
    }

    private VoucherCandidate loadCandidate(UUID userId, String voucherCode, UUID userVoucherId, boolean forUpdate) {
        boolean hasCode = voucherCode != null && !voucherCode.isBlank();
        boolean hasUserVoucher = userVoucherId != null;
        if (hasCode == hasUserVoucher) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Moi don chi duoc dung 1 voucherCode hoac 1 userVoucherId");
        }
        if (hasUserVoucher) {
            UserVoucher userVoucher = (forUpdate
                    ? userVoucherRepository.findByIdAndUserIdForUpdate(userVoucherId, userId)
                    : userVoucherRepository.findByIdAndUserId(userVoucherId, userId))
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Voucher trong vi khong ton tai"));
            return new VoucherCandidate(userVoucher.getCampaign(), userVoucher.getCode(), userVoucher);
        }
        VoucherCode code = (forUpdate
                ? codeRepository.findByCodeForUpdate(normalizeCode(voucherCode))
                : codeRepository.findByCode(normalizeCode(voucherCode)))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Ma voucher khong ton tai"));
        return new VoucherCandidate(code.getCampaign(), code, null);
    }

    private VoucherValidationResponse quote(User user, SurpriseBag bag, BigDecimal subtotal,
                                            VoucherCandidate candidate, boolean strict) {
        VoucherCampaign campaign = candidate.campaign();
        validateCampaignForBag(user, bag, subtotal, candidate, strict);
        BigDecimal discount = calculateDiscount(campaign, subtotal);
        BigDecimal platformFunded = discount.multiply(BigDecimal.valueOf(campaign.getPlatformFundingBps()))
                .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP);
        BigDecimal merchantFunded = discount.subtract(platformFunded);
        return VoucherValidationResponse.builder()
                .campaignId(campaign.getId())
                .voucherCodeId(candidate.code() == null ? null : candidate.code().getId())
                .userVoucherId(candidate.userVoucher() == null ? null : candidate.userVoucher().getId())
                .voucherCode(candidate.code() == null ? null : candidate.code().getCode())
                .campaignName(campaign.getName())
                .discountType(campaign.getDiscountType())
                .discountValue(campaign.getDiscountValue())
                .fundingSource(campaign.getFundingSource())
                .subtotalAmount(subtotal)
                .discountAmount(discount)
                .platformFundedAmount(platformFunded)
                .merchantFundedAmount(merchantFunded)
                .finalAmount(subtotal.subtract(discount))
                .build();
    }

    private void validateCampaignForBag(User user, SurpriseBag bag, BigDecimal subtotal,
                                        VoucherCandidate candidate, boolean strict) {
        Instant now = Instant.now(clock);
        VoucherCampaign campaign = candidate.campaign();
        if (campaign == null
                || campaign.getStatus() != VoucherCampaignStatus.ACTIVE
                || campaign.getStartsAt().isAfter(now)
                || !campaign.getEndsAt().isAfter(now)) {
            reject(strict, "Voucher khong con hieu luc");
            return;
        }
        if (campaign.getStore() != null && !campaign.getStore().getId().equals(bag.getStore().getId())) {
            reject(strict, "Voucher khong ap dung cho cua hang nay");
        }
        if (campaign.getBag() != null && !campaign.getBag().getId().equals(bag.getId())) {
            reject(strict, "Voucher khong ap dung cho tui nay");
        }
        if (campaign.getCategory() != null && campaign.getCategory() != bag.getCategory()) {
            reject(strict, "Voucher khong ap dung cho danh muc nay");
        }
        if (campaign.getBagType() != null && campaign.getBagType() != bag.getBagType()) {
            reject(strict, "Voucher khong ap dung cho loai tui nay");
        }
        if (campaign.getDietType() != null && campaign.getDietType() != bag.getDietType()) {
            reject(strict, "Voucher khong ap dung cho che do an nay");
        }
        if (subtotal.compareTo(valueOrZero(campaign.getMinOrderAmount())) < 0) {
            reject(strict, "Don hang chua dat gia tri toi thieu de dung voucher");
        }
        VoucherCode code = candidate.code();
        if (code != null && !isCodeUsable(code, now)) {
            reject(strict, "Ma voucher khong con hieu luc");
        }
        UserVoucher userVoucher = candidate.userVoucher();
        if (userVoucher != null
                && (userVoucher.getStatus() != UserVoucherStatus.CLAIMED
                || (userVoucher.getExpiresAt() != null && !userVoucher.getExpiresAt().isAfter(now)))) {
            reject(strict, "Voucher trong vi khong con san sang de dung");
        }
        if (campaign.getTotalUsageLimit() != null
                && campaign.getReservedCount() + campaign.getRedeemedCount() >= campaign.getTotalUsageLimit()) {
            reject(strict, "Campaign da het luot su dung");
        }
        if (code != null && code.getUsageLimit() != null
                && code.getReservedCount() + code.getRedeemedCount() >= code.getUsageLimit()) {
            reject(strict, "Ma voucher da het luot su dung");
        }
        if (campaign.getPerUserLimit() > 0
                && redemptionRepository.countUserCampaignUsage(user.getId(), campaign.getId(), ACTIVE_USAGE_STATUSES)
                >= campaign.getPerUserLimit()) {
            reject(strict, "Ban da dung het so luot voucher cua campaign nay");
        }
        if (campaign.isFirstOrderOnly() && orderRepository.countByUser_IdAndPaidAtIsNotNull(user.getId()) > 0) {
            reject(strict, "Voucher chi ap dung cho don hang dau tien");
        }
        BigDecimal discount = calculateDiscount(campaign, subtotal);
        if (discount.signum() <= 0) {
            reject(strict, "Voucher khong tao duoc gia tri giam hop le");
        }
        if (campaign.getBudgetLimitAmount() != null) {
            BigDecimal projected = campaign.getReservedBudgetAmount()
                    .add(campaign.getRedeemedBudgetAmount())
                    .add(discount);
            if (projected.compareTo(campaign.getBudgetLimitAmount()) > 0) {
                reject(strict, "Campaign da vuot ngan sach");
            }
        }
    }

    private void reject(boolean strict, String message) {
        if (strict) {
            throw new ApiException(ErrorCode.INVALID_INPUT, message);
        }
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private void validateCampaignIsClaimable(VoucherCampaign campaign, VoucherCode code, Instant now) {
        if (campaign.getStatus() != VoucherCampaignStatus.ACTIVE
                || campaign.getStartsAt().isAfter(now)
                || !campaign.getEndsAt().isAfter(now)
                || !isCodeUsable(code, now)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Ma voucher khong con hieu luc de claim");
        }
    }

    private boolean isCodeUsable(VoucherCode code, Instant now) {
        return code.getStatus() == VoucherCodeStatus.ACTIVE
                && (code.getStartsAt() == null || !code.getStartsAt().isAfter(now))
                && (code.getEndsAt() == null || code.getEndsAt().isAfter(now));
    }

    private BigDecimal subtotal(SurpriseBag bag, int quantity) {
        if (quantity <= 0 || quantity > bag.getMaxPerOrder()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "So luong tui khong hop le");
        }
        return pricingService.currentPrice(bag, LocalDate.now(clock))
                .currentSalePrice()
                .multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal calculateDiscount(VoucherCampaign campaign, BigDecimal subtotal) {
        BigDecimal raw = switch (campaign.getDiscountType()) {
            case FIXED_AMOUNT -> campaign.getDiscountValue();
            case PERCENTAGE -> subtotal.multiply(campaign.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        };
        if (campaign.getMaxDiscountAmount() != null && campaign.getMaxDiscountAmount().signum() > 0) {
            raw = raw.min(campaign.getMaxDiscountAmount());
        }
        BigDecimal maxDiscount = subtotal.subtract(MIN_PAYABLE).max(BigDecimal.ZERO);
        return raw.min(maxDiscount).max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
    }

    private VoucherValidationResponse toValidationResponse(VoucherRedemption redemption) {
        return VoucherValidationResponse.builder()
                .campaignId(redemption.getCampaign().getId())
                .voucherCodeId(redemption.getCode() == null ? null : redemption.getCode().getId())
                .userVoucherId(redemption.getUserVoucher() == null ? null : redemption.getUserVoucher().getId())
                .voucherCode(redemption.getCodeSnapshot())
                .campaignName(redemption.getCampaign().getName())
                .discountType(redemption.getDiscountType())
                .discountValue(redemption.getDiscountValue())
                .fundingSource(redemption.getFundingSource())
                .subtotalAmount(redemption.getSubtotalAmount())
                .discountAmount(redemption.getDiscountAmount())
                .platformFundedAmount(redemption.getPlatformFundedAmount())
                .merchantFundedAmount(redemption.getMerchantFundedAmount())
                .finalAmount(redemption.getSubtotalAmount().subtract(redemption.getDiscountAmount()))
                .build();
    }

    public UserVoucherResponse toUserVoucherResponse(UserVoucher userVoucher) {
        return UserVoucherResponse.builder()
                .id(userVoucher.getId())
                .campaignId(userVoucher.getCampaign().getId())
                .campaignName(userVoucher.getCampaign().getName())
                .voucherCodeId(userVoucher.getCode() == null ? null : userVoucher.getCode().getId())
                .voucherCode(userVoucher.getCode() == null ? null : userVoucher.getCode().getCode())
                .status(userVoucher.getStatus())
                .expiresAt(userVoucher.getExpiresAt())
                .reservedOrderId(userVoucher.getReservedOrder() == null ? null : userVoucher.getReservedOrder().getId())
                .redeemedOrderId(userVoucher.getRedeemedOrder() == null ? null : userVoucher.getRedeemedOrder().getId())
                .reservedUntil(userVoucher.getReservedUntil())
                .redeemedAt(userVoucher.getRedeemedAt())
                .createdAt(userVoucher.getCreatedAt())
                .updatedAt(userVoucher.getUpdatedAt())
                .build();
    }

    private Instant earliestExpiry(VoucherCampaign campaign, VoucherCode code) {
        Instant expiry = campaign.getEndsAt();
        if (code != null && code.getEndsAt() != null && code.getEndsAt().isBefore(expiry)) {
            expiry = code.getEndsAt();
        }
        return expiry;
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Ma voucher khong duoc de trong");
        }
        return value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private record VoucherCandidate(VoucherCampaign campaign, VoucherCode code, UserVoucher userVoucher) {
    }
}
