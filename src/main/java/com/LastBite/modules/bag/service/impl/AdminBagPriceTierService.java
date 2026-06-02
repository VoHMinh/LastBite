package com.LastBite.modules.bag.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.bag.dto.admin.BagPriceTierRequest;
import com.LastBite.modules.bag.dto.admin.BagPriceTierResponse;
import com.LastBite.modules.bag.dto.admin.UpdateBagPriceTierRequest;
import com.LastBite.modules.bag.entity.BagPriceTier;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.repository.BagPriceTierRepository;
import com.LastBite.modules.bag.service.AdminBagPriceTierServicePort;
import com.LastBite.modules.store.enums.StoreCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminBagPriceTierService implements AdminBagPriceTierServicePort {

    private final BagPriceTierRepository tierRepository;

    @Transactional(readOnly = true)
    public PageResponse<BagPriceTierResponse> list(Pageable pageable) {
        var page = tierRepository.findAll(pageable).map(this::toResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BagPriceTierResponse get(UUID tierId) {
        return toResponse(getTier(tierId));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public BagPriceTierResponse create(BagPriceTierRequest request) {
        if (tierRepository.existsByCategoryAndBagSize(request.getCategory(), request.getBagSize())) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                    "Gói giá cho danh mục và kích cỡ túi này đã tồn tại");
        }
        validatePriceRange(request.getMinimumValue(), request.getBaseSalePrice(),
                request.getDynamicMinPrice(), request.getDynamicMaxPrice(), request.getPlatformFee());

        BagPriceTier tier = BagPriceTier.builder()
                .category(request.getCategory())
                .bagSize(request.getBagSize())
                .minimumValue(request.getMinimumValue())
                .baseSalePrice(request.getBaseSalePrice())
                .dynamicMinPrice(request.getDynamicMinPrice())
                .dynamicMaxPrice(request.getDynamicMaxPrice())
                .platformFee(request.getPlatformFee())
                .active(request.getActive() == null || request.getActive())
                .build();

        return toResponse(tierRepository.save(tier));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public BagPriceTierResponse update(UUID tierId, UpdateBagPriceTierRequest request) {
        BagPriceTier tier = getTier(tierId);
        StoreCategory category = request.getCategory() == null ? tier.getCategory() : request.getCategory();
        BagSize bagSize = request.getBagSize() == null ? tier.getBagSize() : request.getBagSize();
        if ((category != tier.getCategory() || bagSize != tier.getBagSize())
                && tierRepository.findByCategoryAndBagSize(category, bagSize)
                .filter(existing -> !existing.getId().equals(tier.getId()))
                .isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                    "Gói giá cho danh mục và kích cỡ túi này đã tồn tại");
        }

        BigDecimal minimumValue = request.getMinimumValue() == null ? tier.getMinimumValue() : request.getMinimumValue();
        BigDecimal baseSalePrice = request.getBaseSalePrice() == null ? tier.getBaseSalePrice() : request.getBaseSalePrice();
        BigDecimal dynamicMinPrice = request.getDynamicMinPrice() == null ? tier.getDynamicMinPrice() : request.getDynamicMinPrice();
        BigDecimal dynamicMaxPrice = request.getDynamicMaxPrice() == null ? tier.getDynamicMaxPrice() : request.getDynamicMaxPrice();
        BigDecimal platformFee = request.getPlatformFee() == null ? tier.getPlatformFee() : request.getPlatformFee();
        validatePriceRange(minimumValue, baseSalePrice, dynamicMinPrice, dynamicMaxPrice, platformFee);

        tier.setCategory(category);
        tier.setBagSize(bagSize);
        tier.setMinimumValue(minimumValue);
        tier.setBaseSalePrice(baseSalePrice);
        tier.setDynamicMinPrice(dynamicMinPrice);
        tier.setDynamicMaxPrice(dynamicMaxPrice);
        tier.setPlatformFee(platformFee);
        if (request.getActive() != null) {
            tier.setActive(request.getActive());
        }
        return toResponse(tierRepository.save(tier));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public BagPriceTierResponse activate(UUID tierId) {
        BagPriceTier tier = getTier(tierId);
        tier.setActive(true);
        return toResponse(tierRepository.save(tier));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public BagPriceTierResponse deactivate(UUID tierId) {
        BagPriceTier tier = getTier(tierId);
        tier.setActive(false);
        return toResponse(tierRepository.save(tier));
    }

    private BagPriceTier getTier(UUID tierId) {
        return tierRepository.findById(tierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy gói giá"));
    }

    private void validatePriceRange(BigDecimal minimumValue, BigDecimal baseSalePrice,
                                    BigDecimal dynamicMinPrice, BigDecimal dynamicMaxPrice,
                                    BigDecimal platformFee) {
        if (platformFee.signum() < 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Phí nền tảng không được âm");
        }
        if (dynamicMinPrice.compareTo(baseSalePrice) > 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Giá động thấp nhất phải nhỏ hơn hoặc bằng giá bán chuẩn");
        }
        if (baseSalePrice.compareTo(dynamicMaxPrice) > 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Giá bán chuẩn phải nhỏ hơn hoặc bằng giá động cao nhất");
        }
        if (dynamicMaxPrice.compareTo(minimumValue) >= 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Giá động cao nhất phải nhỏ hơn giá trị tối thiểu của túi");
        }
    }

    private BagPriceTierResponse toResponse(BagPriceTier tier) {
        return BagPriceTierResponse.builder()
                .id(tier.getId())
                .category(tier.getCategory())
                .bagSize(tier.getBagSize())
                .minimumValue(tier.getMinimumValue())
                .baseSalePrice(tier.getBaseSalePrice())
                .dynamicMinPrice(tier.getDynamicMinPrice())
                .dynamicMaxPrice(tier.getDynamicMaxPrice())
                .platformFee(tier.getPlatformFee())
                .active(tier.isActive())
                .createdAt(tier.getCreatedAt())
                .updatedAt(tier.getUpdatedAt())
                .build();
    }
}
