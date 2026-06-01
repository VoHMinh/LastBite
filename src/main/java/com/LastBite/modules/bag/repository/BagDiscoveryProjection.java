package com.LastBite.modules.bag.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface BagDiscoveryProjection {
    UUID getBagId();
    UUID getStoreId();
    String getStoreName();
    String getStoreSlug();
    String getStoreAddress();
    String getDistrict();
    String getCity();
    Double getLat();
    Double getLng();
    String getName();
    String getDescription();
    String getBagType();
    String getDietType();
    String getCategory();
    String getBagSize();
    String getPhotos();
    BigDecimal getMinimumValue();
    BigDecimal getBaseSalePrice();
    BigDecimal getDynamicMinPrice();
    BigDecimal getDynamicMaxPrice();
    Boolean getDynamicPricingEnabled();
    BigDecimal getPlatformFee();
    Integer getMaxPerOrder();
    LocalTime getPickupStartTime();
    LocalTime getPickupEndTime();
    String getStatus();
    UUID getDailyStockId();
    LocalDate getStockDate();
    Integer getQuantity();
    Integer getReserved();
    Integer getSold();
    Integer getAvailable();
    String getStockStatus();
    Double getDistanceKm();
}
