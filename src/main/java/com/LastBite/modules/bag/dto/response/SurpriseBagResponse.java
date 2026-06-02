package com.LastBite.modules.bag.dto.response;

import com.LastBite.modules.bag.enums.BagStatus;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.store.enums.StoreCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SurpriseBagResponse {
    private UUID id;
    private UUID storeId;
    private String storeName;
    private String name;
    private String description;
    private BagType bagType;
    private DietType dietType;
    private StoreCategory category;
    private BagSize bagSize;
    private List<String> photos;
    private BigDecimal minimumValue;
    private BigDecimal baseSalePrice;
    private BigDecimal currentSalePrice;
    private BigDecimal savingsAmount;
    private int currentDiscountPercent;
    private BigDecimal dynamicMinPrice;
    private BigDecimal dynamicMaxPrice;
    private boolean dynamicPricingEnabled;
    private BigDecimal platformFee;
    private int maxPerOrder;
    private boolean containerProvided;
    private boolean carrierBagProvided;
    private String packagingNote;
    private LocalTime pickupStartTime;
    private LocalTime pickupEndTime;
    private List<Integer> availableDays;
    private BagStatus status;
    private int version;
    private DailyStockResponse todayStock;
    private Instant createdAt;
    private Instant updatedAt;
}
