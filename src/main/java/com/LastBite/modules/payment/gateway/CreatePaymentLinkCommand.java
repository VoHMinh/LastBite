package com.LastBite.modules.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePaymentLinkCommand(
        Long orderCode,
        BigDecimal amount,
        String description,
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        String itemName,
        int quantity,
        String returnUrl,
        String cancelUrl,
        Instant expiresAt
) {
}
