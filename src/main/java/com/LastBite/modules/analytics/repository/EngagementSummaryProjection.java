package com.LastBite.modules.analytics.repository;

public interface EngagementSummaryProjection {
    long getStoreViews();
    long getStoreCardClicks();
    long getBagViews();
    long getBagCardClicks();
    long getKnownUsers();
    long getAnonymousSessions();
}
