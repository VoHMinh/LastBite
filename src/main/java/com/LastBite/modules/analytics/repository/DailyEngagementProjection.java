package com.LastBite.modules.analytics.repository;

public interface DailyEngagementProjection {
    String getDate();
    long getStoreViews();
    long getBagViews();
    long getCardClicks();
    long getTotalEvents();
}
