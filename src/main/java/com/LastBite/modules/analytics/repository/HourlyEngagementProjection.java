package com.LastBite.modules.analytics.repository;

public interface HourlyEngagementProjection {
    int getHour();
    long getStoreViews();
    long getBagViews();
    long getCardClicks();
    long getTotalEvents();
}
