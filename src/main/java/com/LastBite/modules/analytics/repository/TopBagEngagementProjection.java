package com.LastBite.modules.analytics.repository;

import java.util.UUID;

public interface TopBagEngagementProjection {
    UUID getBagId();
    String getBagName();
    long getViews();
    long getCardClicks();
    long getTotalEvents();
}
