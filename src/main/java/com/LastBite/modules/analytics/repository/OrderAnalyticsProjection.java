package com.LastBite.modules.analytics.repository;

import java.math.BigDecimal;

public interface OrderAnalyticsProjection {
    long getTotalOrders();
    long getPaidOrders();
    long getBagsSold();
    BigDecimal getGrossRevenue();
}
