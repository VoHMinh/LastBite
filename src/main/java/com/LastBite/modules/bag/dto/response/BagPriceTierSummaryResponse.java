package com.LastBite.modules.bag.dto.response;

import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.store.enums.StoreCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO cho cửa hàng xem danh sách gói giá (BagPriceTier) đang active.
 * Dùng khi merchant chọn loại túi lúc tạo SurpriseBag.
 */
@Getter
@Builder
public class BagPriceTierSummaryResponse {
    private UUID id;
    private StoreCategory category;
    private BagSize bagSize;
    private BigDecimal minimumValue;
    private BigDecimal baseSalePrice;
    private BigDecimal dynamicMinPrice;
    private BigDecimal dynamicMaxPrice;
    private BigDecimal platformFee;
}
