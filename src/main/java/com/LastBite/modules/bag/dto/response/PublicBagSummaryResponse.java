package com.LastBite.modules.bag.dto.response;

import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.store.enums.StoreCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicBagSummaryResponse implements Serializable {
    private UUID bagId;
    private UUID storeId;
    private String storeName;
    private String storeSlug;
    private String storeAddress;
    private String district;
    private String city;
    private Double lat;
    private Double lng;
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
    private LocalDate stockDate;
    private LocalTime pickupStartTime;
    private LocalTime pickupEndTime;
    private int quantity;
    private int reserved;
    private int sold;
    private int available;
    private boolean soldOut;
    private Double distanceKm;
    private long minutesUntilPickup;
    private boolean pickupActive;
}
