package com.LastBite.common.email;

public enum EmailDeliveryStatus {
    PENDING,
    SENT,
    DELIVERED,
    DELIVERY_DELAYED,
    FAILED,
    BOUNCED,
    COMPLAINED,
    SUPPRESSED
}
