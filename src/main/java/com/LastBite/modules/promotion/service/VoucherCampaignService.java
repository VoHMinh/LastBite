package com.LastBite.modules.promotion.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.audit.service.AdminAuditLogService;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.merchant.service.StoreAccessService;
import com.LastBite.modules.promotion.dto.request.AddVoucherCodesRequest;
import com.LastBite.modules.promotion.dto.request.VoucherCampaignUpsertRequest;
import com.LastBite.modules.promotion.dto.response.VoucherCampaignAnalyticsResponse;
import com.LastBite.modules.promotion.dto.response.VoucherCampaignResponse;
import com.LastBite.modules.promotion.dto.response.VoucherRedemptionResponse;
import com.LastBite.modules.promotion.entity.VoucherCampaign;
import com.LastBite.modules.promotion.entity.VoucherCode;
import com.LastBite.modules.promotion.entity.VoucherRedemption;
import com.LastBite.modules.promotion.enums.*;
import com.LastBite.modules.promotion.repository.VoucherCampaignRepository;
import com.LastBite.modules.promotion.repository.VoucherCodeRepository;
import com.LastBite.modules.promotion.repository.VoucherRedemptionRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VoucherCampaignService {

    private static final Set<UserRole> CAMPAIGN_ROLES = Set.of(UserRole.MANAGER);

    private final VoucherCampaignRepository campaignRepository;
    private final VoucherCodeRepository codeRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final StoreRepository storeRepository;
    private final SurpriseBagRepository bagRepository;
    private final UserRepository userRepository;
    private final StoreAccessService storeAccessService;
    private final AdminAuditLogService auditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<VoucherCampaignResponse> listAdmin(VoucherCampaignStatus status,
                                                           VoucherCampaignOwnerType ownerType,
                                                           VoucherFundingSource fundingSource,
                                                           Pageable pageable) {
        var page = campaignRepository.searchAdmin(status, ownerType, fundingSource, pageable)
                .map(this::toCampaignResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public VoucherCampaignResponse createAdmin(UUID actorId, VoucherCampaignUpsertRequest request) {
        User actor = user(actorId);
        VoucherCampaign campaign = buildCampaign(actor, request,
                request.getOwnerType() == null ? VoucherCampaignOwnerType.PLATFORM : request.getOwnerType(),
                request.getStoreId() == null ? null : store(request.getStoreId()),
                false);
        campaign = campaignRepository.save(campaign);
        auditLogService.record(actor, "VOUCHER_CAMPAIGN_CREATE", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Admin created campaign", "funding=" + campaign.getFundingSource());
        return toCampaignResponse(campaign);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public VoucherCampaignResponse updateAdmin(UUID actorId, UUID campaignId, VoucherCampaignUpsertRequest request) {
        User actor = user(actorId);
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        apply(campaign, request, request.getStoreId() == null ? null : store(request.getStoreId()), false);
        auditLogService.record(actor, "VOUCHER_CAMPAIGN_UPDATE", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Admin updated campaign", "status=" + campaign.getStatus());
        return toCampaignResponse(campaign);
    }

    @Transactional
    public VoucherCampaignResponse approve(UUID actorId, UUID campaignId) {
        User actor = user(actorId);
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        if (campaign.getStatus() == VoucherCampaignStatus.ENDED || campaign.getStatus() == VoucherCampaignStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Campaign da ket thuc khong the approve");
        }
        campaign.setStatus(VoucherCampaignStatus.APPROVED);
        campaign.setApprovedBy(actor);
        campaign.setApprovedAt(Instant.now(clock));
        auditLogService.record(actor, "VOUCHER_CAMPAIGN_APPROVE", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Admin approved campaign", "funding=" + campaign.getFundingSource());
        return toCampaignResponse(campaign);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public VoucherCampaignResponse publishAdmin(UUID actorId, UUID campaignId) {
        User actor = user(actorId);
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        publish(campaign, actor);
        auditLogService.record(actor, "VOUCHER_CAMPAIGN_PUBLISH", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Admin published campaign", "status=" + campaign.getStatus());
        return toCampaignResponse(campaign);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public VoucherCampaignResponse pauseAdmin(UUID actorId, UUID campaignId) {
        User actor = user(actorId);
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        pause(campaign);
        auditLogService.record(actor, "VOUCHER_CAMPAIGN_PAUSE", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Admin paused campaign", null);
        return toCampaignResponse(campaign);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public VoucherCampaignResponse endAdmin(UUID actorId, UUID campaignId) {
        User actor = user(actorId);
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        end(campaign);
        auditLogService.record(actor, "VOUCHER_CAMPAIGN_END", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Admin ended campaign", null);
        return toCampaignResponse(campaign);
    }

    @Transactional
    public List<String> addCodes(UUID actorId, UUID campaignId, AddVoucherCodesRequest request) {
        User actor = user(actorId);
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        List<String> normalized = request.getCodes().stream()
                .map(this::normalizeCode)
                .distinct()
                .toList();
        for (String code : normalized) {
            if (codeRepository.existsByCode(code)) {
                throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Ma voucher da ton tai: " + code);
            }
        }
        normalized.forEach(code -> codeRepository.save(VoucherCode.builder()
                .campaign(campaign)
                .code(code)
                .codeType(request.getCodeType() == null ? VoucherCodeType.PUBLIC : request.getCodeType())
                .status(VoucherCodeStatus.ACTIVE)
                .usageLimit(request.getUsageLimit())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .build()));
        auditLogService.record(actor, "VOUCHER_CODES_ADD", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Added voucher codes", "count=" + normalized.size());
        return normalized;
    }

    @Transactional
    public List<String> addMerchantCodes(UUID actorId, UUID storeId, UUID campaignId, AddVoucherCodesRequest request) {
        storeAccessService.require(actorId, storeId, CAMPAIGN_ROLES);
        VoucherCampaign campaign = merchantCampaignForUpdate(campaignId, storeId);
        return addCodes(actorId, campaign.getId(), request);
    }
    @Transactional(readOnly = true)
    public PageResponse<VoucherCampaignResponse> listMerchant(UUID actorId, UUID storeId,
                                                              VoucherCampaignStatus status,
                                                              Pageable pageable) {
        storeAccessService.require(actorId, storeId, CAMPAIGN_ROLES);
        var page = campaignRepository.searchMerchant(storeId, status, pageable).map(this::toCampaignResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public VoucherCampaignResponse createMerchant(UUID actorId, UUID storeId, VoucherCampaignUpsertRequest request) {
        Store store = storeAccessService.require(actorId, storeId, CAMPAIGN_ROLES);
        User actor = user(actorId);
        if (request.getFundingSource() == VoucherFundingSource.PLATFORM) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Merchant khong the tu tao platform-funded voucher");
        }
        VoucherCampaign campaign = buildCampaign(actor, request, VoucherCampaignOwnerType.MERCHANT, store, true);
        campaign = campaignRepository.save(campaign);
        auditLogService.record(actor, "MERCHANT_VOUCHER_CAMPAIGN_CREATE", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Merchant created campaign", "storeId=" + storeId);
        return toCampaignResponse(campaign);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public VoucherCampaignResponse updateMerchant(UUID actorId, UUID storeId, UUID campaignId,
                                                  VoucherCampaignUpsertRequest request) {
        Store store = storeAccessService.require(actorId, storeId, CAMPAIGN_ROLES);
        User actor = user(actorId);
        VoucherCampaign campaign = merchantCampaignForUpdate(campaignId, storeId);
        if (request.getFundingSource() == VoucherFundingSource.PLATFORM) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Merchant khong the chuyen campaign sang platform-funded");
        }
        apply(campaign, request, store, true);
        auditLogService.record(actor, "MERCHANT_VOUCHER_CAMPAIGN_UPDATE", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Merchant updated campaign", "storeId=" + storeId);
        return toCampaignResponse(campaign);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public VoucherCampaignResponse publishMerchant(UUID actorId, UUID storeId, UUID campaignId) {
        storeAccessService.require(actorId, storeId, CAMPAIGN_ROLES);
        User actor = user(actorId);
        VoucherCampaign campaign = merchantCampaignForUpdate(campaignId, storeId);
        if (campaign.getFundingSource() == VoucherFundingSource.SHARED
                && campaign.getStatus() != VoucherCampaignStatus.APPROVED) {
            campaign.setStatus(VoucherCampaignStatus.PENDING_APPROVAL);
            auditLogService.record(actor, "MERCHANT_VOUCHER_CAMPAIGN_SUBMIT", "VOUCHER_CAMPAIGN", campaign.getId(),
                    "Merchant submitted shared campaign for approval", "storeId=" + storeId);
            return toCampaignResponse(campaign);
        }
        publish(campaign, actor);
        auditLogService.record(actor, "MERCHANT_VOUCHER_CAMPAIGN_PUBLISH", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Merchant published campaign", "storeId=" + storeId);
        return toCampaignResponse(campaign);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public VoucherCampaignResponse pauseMerchant(UUID actorId, UUID storeId, UUID campaignId) {
        storeAccessService.require(actorId, storeId, CAMPAIGN_ROLES);
        User actor = user(actorId);
        VoucherCampaign campaign = merchantCampaignForUpdate(campaignId, storeId);
        pause(campaign);
        auditLogService.record(actor, "MERCHANT_VOUCHER_CAMPAIGN_PAUSE", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Merchant paused campaign", "storeId=" + storeId);
        return toCampaignResponse(campaign);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public VoucherCampaignResponse endMerchant(UUID actorId, UUID storeId, UUID campaignId) {
        storeAccessService.require(actorId, storeId, CAMPAIGN_ROLES);
        User actor = user(actorId);
        VoucherCampaign campaign = merchantCampaignForUpdate(campaignId, storeId);
        end(campaign);
        auditLogService.record(actor, "MERCHANT_VOUCHER_CAMPAIGN_END", "VOUCHER_CAMPAIGN", campaign.getId(),
                "Merchant ended campaign", "storeId=" + storeId);
        return toCampaignResponse(campaign);
    }

    @Transactional(readOnly = true)
    public PageResponse<VoucherRedemptionResponse> listAdminRedemptions(UUID campaignId,
                                                                        VoucherRedemptionStatus status,
                                                                        Pageable pageable) {
        var page = redemptionRepository.searchAdmin(campaignId, status, pageable)
                .map(this::toRedemptionResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<VoucherRedemptionResponse> listMerchantRedemptions(UUID actorId, UUID storeId,
                                                                           UUID campaignId,
                                                                           VoucherRedemptionStatus status,
                                                                           Pageable pageable) {
        storeAccessService.require(actorId, storeId, CAMPAIGN_ROLES);
        var page = redemptionRepository.searchMerchant(storeId, campaignId, status, pageable)
                .map(this::toRedemptionResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public VoucherCampaignAnalyticsResponse analytics(UUID campaignId) {
        campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        Object[] row = redemptionRepository.analytics(campaignId);
        Object[] values = row.length == 1 && row[0] instanceof Object[] nested ? nested : row;
        return VoucherCampaignAnalyticsResponse.builder()
                .campaignId(campaignId)
                .redeemedOrders(((Number) values[0]).longValue())
                .totalDiscountAmount((BigDecimal) values[1])
                .platformFundedAmount((BigDecimal) values[2])
                .merchantFundedAmount((BigDecimal) values[3])
                .build();
    }

    private VoucherCampaign buildCampaign(User actor, VoucherCampaignUpsertRequest request,
                                          VoucherCampaignOwnerType ownerType, Store store,
                                          boolean merchantOwned) {
        validateCampaignRequest(request, merchantOwned);
        VoucherCampaign campaign = VoucherCampaign.builder()
                .ownerType(ownerType)
                .createdBy(actor)
                .status(initialStatus(request.getFundingSource(), merchantOwned))
                .build();
        apply(campaign, request, store, merchantOwned);
        return campaign;
    }

    private void apply(VoucherCampaign campaign, VoucherCampaignUpsertRequest request,
                       Store store, boolean merchantOwned) {
        validateCampaignRequest(request, merchantOwned);
        campaign.setStore(store);
        campaign.setBag(loadBagForScope(request.getBagId(), store));
        campaign.setCategory(request.getCategory());
        campaign.setBagType(request.getBagType());
        campaign.setDietType(request.getDietType());
        campaign.setName(request.getName().trim());
        campaign.setDescription(trimToNull(request.getDescription()));
        campaign.setDiscountType(request.getDiscountType());
        campaign.setDiscountValue(request.getDiscountValue());
        campaign.setMaxDiscountAmount(zeroToNull(request.getMaxDiscountAmount()));
        campaign.setMinOrderAmount(valueOrZero(request.getMinOrderAmount()));
        campaign.setFundingSource(request.getFundingSource());
        applyFunding(campaign, request);
        campaign.setStartsAt(request.getStartsAt());
        campaign.setEndsAt(request.getEndsAt());
        campaign.setBudgetLimitAmount(zeroToNull(request.getBudgetLimitAmount()));
        campaign.setTotalUsageLimit(request.getTotalUsageLimit());
        campaign.setPerUserLimit(request.getPerUserLimit() == null ? 1 : request.getPerUserLimit());
        campaign.setFirstOrderOnly(request.isFirstOrderOnly());
        if (merchantOwned && campaign.getFundingSource() == VoucherFundingSource.SHARED
                && campaign.getStatus() != VoucherCampaignStatus.ACTIVE
                && campaign.getStatus() != VoucherCampaignStatus.APPROVED) {
            campaign.setStatus(VoucherCampaignStatus.PENDING_APPROVAL);
        }
    }

    private void validateCampaignRequest(VoucherCampaignUpsertRequest request, boolean merchantOwned) {
        if (request.getStartsAt() == null || request.getEndsAt() == null || !request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Thoi gian campaign khong hop le");
        }
        if (request.getDiscountType() == VoucherDiscountType.PERCENTAGE
                && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Voucher phan tram khong duoc vuot qua 100%");
        }
        if (merchantOwned && request.getFundingSource() == VoucherFundingSource.PLATFORM) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Merchant khong the tao platform-funded voucher");
        }
    }

    private void applyFunding(VoucherCampaign campaign, VoucherCampaignUpsertRequest request) {
        VoucherFundingSource source = request.getFundingSource();
        if (source == VoucherFundingSource.PLATFORM) {
            campaign.setPlatformFundingBps(10000);
            campaign.setMerchantFundingBps(0);
        } else if (source == VoucherFundingSource.MERCHANT) {
            campaign.setPlatformFundingBps(0);
            campaign.setMerchantFundingBps(10000);
        } else {
            int platformBps = request.getPlatformFundingBps() == null ? 5000 : request.getPlatformFundingBps();
            int merchantBps = request.getMerchantFundingBps() == null ? 10000 - platformBps : request.getMerchantFundingBps();
            if (platformBps < 0 || merchantBps < 0 || platformBps + merchantBps != 10000) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Shared funding bps phai cong bang 10000");
            }
            campaign.setPlatformFundingBps(platformBps);
            campaign.setMerchantFundingBps(merchantBps);
        }
    }

    private VoucherCampaignStatus initialStatus(VoucherFundingSource fundingSource, boolean merchantOwned) {
        if (merchantOwned && fundingSource == VoucherFundingSource.SHARED) {
            return VoucherCampaignStatus.PENDING_APPROVAL;
        }
        return VoucherCampaignStatus.DRAFT;
    }

    private void publish(VoucherCampaign campaign, User actor) {
        if (campaign.getStatus() == VoucherCampaignStatus.ACTIVE) {
            return;
        }
        if (campaign.getStatus() == VoucherCampaignStatus.ENDED || campaign.getStatus() == VoucherCampaignStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Campaign da ket thuc khong the publish");
        }
        if (campaign.getFundingSource() == VoucherFundingSource.SHARED
                && campaign.getOwnerType() == VoucherCampaignOwnerType.MERCHANT
                && campaign.getStatus() != VoucherCampaignStatus.APPROVED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Shared campaign can admin approve truoc khi publish");
        }
        campaign.setStatus(VoucherCampaignStatus.ACTIVE);
        campaign.setPublishedAt(Instant.now(clock));
        if (campaign.getApprovedBy() == null) {
            campaign.setApprovedBy(actor);
            campaign.setApprovedAt(Instant.now(clock));
        }
    }

    private void pause(VoucherCampaign campaign) {
        if (campaign.getStatus() != VoucherCampaignStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi campaign ACTIVE moi co the pause");
        }
        campaign.setStatus(VoucherCampaignStatus.PAUSED);
    }

    private void end(VoucherCampaign campaign) {
        if (campaign.getStatus() == VoucherCampaignStatus.ENDED) {
            return;
        }
        campaign.setStatus(VoucherCampaignStatus.ENDED);
        campaign.setEndedAt(Instant.now(clock));
    }

    private VoucherCampaign merchantCampaignForUpdate(UUID campaignId, UUID storeId) {
        VoucherCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign khong ton tai"));
        if (campaign.getStore() == null || !campaign.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return campaign;
    }

    private SurpriseBag loadBagForScope(UUID bagId, Store store) {
        if (bagId == null) return null;
        SurpriseBag bag = bagRepository.findById(bagId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
        if (store != null && !bag.getStore().getId().equals(store.getId())) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Bag scope khong thuoc store campaign");
        }
        return bag;
    }

    private Store store(UUID storeId) {
        return storeRepository.findDetailById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
    }

    private User user(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    public VoucherCampaignResponse toCampaignResponse(VoucherCampaign campaign) {
        return VoucherCampaignResponse.builder()
                .id(campaign.getId())
                .ownerType(campaign.getOwnerType())
                .storeId(campaign.getStore() == null ? null : campaign.getStore().getId())
                .storeName(campaign.getStore() == null ? null : campaign.getStore().getName())
                .bagId(campaign.getBag() == null ? null : campaign.getBag().getId())
                .bagName(campaign.getBag() == null ? null : campaign.getBag().getName())
                .category(campaign.getCategory())
                .bagType(campaign.getBagType())
                .dietType(campaign.getDietType())
                .status(campaign.getStatus())
                .name(campaign.getName())
                .description(campaign.getDescription())
                .discountType(campaign.getDiscountType())
                .discountValue(campaign.getDiscountValue())
                .maxDiscountAmount(campaign.getMaxDiscountAmount())
                .minOrderAmount(campaign.getMinOrderAmount())
                .fundingSource(campaign.getFundingSource())
                .platformFundingBps(campaign.getPlatformFundingBps())
                .merchantFundingBps(campaign.getMerchantFundingBps())
                .startsAt(campaign.getStartsAt())
                .endsAt(campaign.getEndsAt())
                .budgetLimitAmount(campaign.getBudgetLimitAmount())
                .reservedBudgetAmount(campaign.getReservedBudgetAmount())
                .redeemedBudgetAmount(campaign.getRedeemedBudgetAmount())
                .totalUsageLimit(campaign.getTotalUsageLimit())
                .perUserLimit(campaign.getPerUserLimit())
                .reservedCount(campaign.getReservedCount())
                .redeemedCount(campaign.getRedeemedCount())
                .firstOrderOnly(campaign.isFirstOrderOnly())
                .createdByUserId(campaign.getCreatedBy() == null ? null : campaign.getCreatedBy().getId())
                .approvedByUserId(campaign.getApprovedBy() == null ? null : campaign.getApprovedBy().getId())
                .approvedAt(campaign.getApprovedAt())
                .publishedAt(campaign.getPublishedAt())
                .endedAt(campaign.getEndedAt())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    public VoucherRedemptionResponse toRedemptionResponse(VoucherRedemption redemption) {
        return VoucherRedemptionResponse.builder()
                .id(redemption.getId())
                .orderId(redemption.getOrder().getId())
                .orderNumber(redemption.getOrder().getOrderNumber())
                .userId(redemption.getUser().getId())
                .userName(redemption.getUser().getFullName())
                .campaignId(redemption.getCampaign().getId())
                .campaignName(redemption.getCampaign().getName())
                .voucherCodeId(redemption.getCode() == null ? null : redemption.getCode().getId())
                .voucherCode(redemption.getCodeSnapshot())
                .userVoucherId(redemption.getUserVoucher() == null ? null : redemption.getUserVoucher().getId())
                .fundingSource(redemption.getFundingSource())
                .status(redemption.getStatus())
                .subtotalAmount(redemption.getSubtotalAmount())
                .discountAmount(redemption.getDiscountAmount())
                .platformFundedAmount(redemption.getPlatformFundedAmount())
                .merchantFundedAmount(redemption.getMerchantFundedAmount())
                .reservedAt(redemption.getReservedAt())
                .reservedUntil(redemption.getReservedUntil())
                .redeemedAt(redemption.getRedeemedAt())
                .releasedAt(redemption.getReleasedAt())
                .reissuedAt(redemption.getReissuedAt())
                .releaseReason(redemption.getReleaseReason())
                .createdAt(redemption.getCreatedAt())
                .updatedAt(redemption.getUpdatedAt())
                .build();
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Ma voucher khong duoc de trong");
        }
        return value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal zeroToNull(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value;
    }
}
